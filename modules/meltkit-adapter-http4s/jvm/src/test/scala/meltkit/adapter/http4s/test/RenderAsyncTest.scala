/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import melt.runtime.render.{ RenderResult, ServerRenderer }
import melt.runtime.Async

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.adapter.http4s.Http4sMeltContext
import meltkit.codec.BodyDecoder
import org.http4s.{ Query as _, * }
import org.http4s.implicits.*

/** End-to-end test of blocking async SSR (`ctx.renderAsync`) through the http4s
  * context, mirroring what `<melt:await>` codegen emits: the shell pushes a marker
  * span + pending fallback and registers a boundary with the ambient
  * [[SsrRenderScope]]; `renderAsync` resolves the query in-process (via the app's
  * `app.serve` registry), splices the resolved branch over the marker, and injects
  * the hydration seed. No real `.melt` file or HTTP loopback is involved.
  */
class RenderAsyncTest extends CatsEffectSuite:

  private val list = ServerFn.query[Unit, List[Int]]("nums.list")

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def ctxWith(app: MeltKit[IO]): Http4sMeltContext[IO, PathSpec.Empty, Unit] =
    new Http4sMeltContext[IO, PathSpec.Empty, Unit](
      PathSpec.emptyValue,
      Request[IO](Method.GET, uri"/await"),
      summon[BodyDecoder[Unit]],
      Some(template),
      ViteManifest.empty,
      "en",
      "",
      new Locals(),
      None,
      Some(app)
    )

  /** Mirrors generated `<melt:await value={q}>` SSR code: marker span + pending
    * fallback pushed in place, resolved-branch renderer registered on the scope. */
  private def awaitShell(q: Query[List[Int]]): RenderResult =
    val r  = ServerRenderer()
    val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
    r.push("<main>")
    r.push("<!--melt:sb:" + id + "-->")
    r.push("<p class=\"loading\">Loading…</p>")
    r.push("<!--/melt:sb:" + id + "-->")
    r.push("</main>")
    SsrRenderScope.current.foreach(
      _.suspend(
        id,
        q,
        {
          case Async.Done(xs)  => RenderResult("<ul>" + xs.map(n => s"<li>$n</li>").mkString + "</ul>", "")
          case Async.Failed(_) => RenderResult("<p class=\"error\">failed</p>", "")
          case Async.Loading   => RenderResult("", "")
        }
      )
    )
    r.result()

  test("renderAsync resolves the query in-process, splices the branch, and seeds hydration"):
    val app = MeltKit[IO]()
    app.serve(list) { (_, _) => IO.pure(List(1, 2, 3)) }
    val ctx = ctxWith(app)

    ctx.renderAsync(awaitShell(list())).map { resp =>
      val html = resp.body
      // resolved Done branch spliced over the marker span (pending gone)
      assert(html.contains("<ul><li>1</li><li>2</li><li>3</li></ul>"), html)
      assert(!html.contains("Loading…"), html)
      assert(!html.contains("melt:sb:"), html)
      // hydration seed injected so the client adopts the data without refetching
      assert(html.contains("data-melt-queries"), html)
      assert(html.contains("\"nums.list\\nnull\":[1,2,3]"), html)
    }

  test("a failing query renders its Failed branch, not a 500, and is not seeded"):
    val app = MeltKit[IO]()
    app.serve(list) { (_, _) => IO.raiseError(new RuntimeException("db down")) }
    val ctx = ctxWith(app)

    ctx.renderAsync(awaitShell(list())).map { resp =>
      val html = resp.body
      assertEquals(resp.status: Int, 200)
      assert(html.contains("<p class=\"error\">failed</p>"), html)
      assert(!html.contains("data-melt-queries"), html) // failures are not seeded
    }

  test("a shell with no boundary is rendered synchronously (no seed script)"):
    val app = MeltKit[IO]()
    app.serve(list) { (_, _) => IO.pure(Nil) }
    val ctx = ctxWith(app)

    ctx
      .renderAsync {
        val r = ServerRenderer()
        r.push("<main>static</main>")
        r.result()
      }
      .map { resp =>
        assert(resp.body.contains("<main>static</main>"), resp.body)
        assert(!resp.body.contains("data-melt-queries"), resp.body)
      }
