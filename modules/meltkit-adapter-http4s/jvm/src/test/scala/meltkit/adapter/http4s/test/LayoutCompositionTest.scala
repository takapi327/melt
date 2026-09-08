/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import munit.CatsEffectSuite

import melt.runtime.render.RenderResult

import cats.effect.IO
import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.adapter.http4s.Http4sMeltContext
import meltkit.codec.BodyDecoder
import org.http4s.*
import org.http4s.implicits.*

/** Which render entry points compose the layouts registered with `app.layout`. */
class LayoutCompositionTest extends CatsEffectSuite:

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def appWithLayout: MeltKit[IO] =
    val a = MeltKit[IO]()
    a.layout("")(child => RenderResult("<shell>" + child().body + "</shell>", ""))
    a

  private def ctxWith(app: MeltKit[IO]): Http4sMeltContext[IO, PathSpec.Empty, Unit] =
    new Http4sMeltContext[IO, PathSpec.Empty, Unit](
      PathSpec.emptyValue,
      Request[IO](Method.GET, uri"/page"),
      summon[BodyDecoder[Unit]],
      Some(template),
      ViteManifest.empty,
      "en",
      "",
      new Locals(),
      None,
      Some(app)
    )

  private val page = RenderResult("<p>page</p>", "")

  test("ctx.render composes the registered layout"):
    val body = ctxWith(appWithLayout).render(page).body
    assert(body.contains("<shell><p>page</p></shell>"), body)

  test("ctx.renderAsync composes the registered layout"):
    ctxWith(appWithLayout).renderAsync(page).map { res =>
      assert(res.body.contains("<shell><p>page</p></shell>"), res.body)
    }

  test("ctx.renderStream composes the registered layout"):
    ctxWith(appWithLayout).renderStream(page).map { res =>
      assert(res.body.contains("<shell><p>page</p></shell>"), res.body)
    }

  test("an await inside a layout resolves through renderAsync"):
    // The layout is what needs per-request data; the page below it may need none.
    val nums = ServerFn.query[Unit, List[Int]]("layout.nums")
    val a    = MeltKit[IO]()
    a.serve(nums) { (_, _) => IO.pure(List(1, 2)) }
    a.layout("") { child =>
      val r  = melt.runtime.render.ServerRenderer()
      val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
      r.push("<nav>")
      r.push("<!--melt:sb:" + id + "-->")
      r.push("<span>…</span>")
      r.push("<!--/melt:sb:" + id + "-->")
      r.push("</nav>")
      SsrRenderScope.current.foreach(
        _.suspend(
          id,
          nums(),
          {
            case melt.runtime.Async.Done(xs) => RenderResult(xs.mkString("<b>", ",", "</b>"), "")
            case _                           => RenderResult("", "")
          }
        )
      )
      RenderResult(r.result().body + child().body, "")
    }

    ctxWith(a).renderAsync(page).map { res =>
      assert(res.body.contains("<b>1,2</b>"), res.body)
      assert(res.body.contains("<p>page</p>"), res.body)
    }

  /** A boundary awaiting `q`, rendered with `label` around the resolved value. */
  private def boundary(r: melt.runtime.render.ServerRenderer, label: String, q: meltkit.Query[List[Int]]): Unit =
    val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
    r.push("<!--melt:sb:" + id + "-->")
    r.push("<i></i>")
    r.push("<!--/melt:sb:" + id + "-->")
    SsrRenderScope.current.foreach(
      _.suspend(
        id,
        q,
        {
          case melt.runtime.Async.Done(xs) => RenderResult(s"<$label>" + xs.mkString(",") + s"</$label>", "")
          case _                           => RenderResult("", "")
        }
      )
    )

  test("the same query awaited by a layout and its page runs once"):
    val nums  = ServerFn.query[Unit, List[Int]]("shared.nums")
    val calls = new java.util.concurrent.atomic.AtomicInteger(0)

    val a = MeltKit[IO]()
    a.serve(nums) { (_, _) => IO(calls.incrementAndGet()).as(List(1, 2)) }
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      r.push("<nav>")
      boundary(r, "nav", nums())
      r.push("</nav>")
      RenderResult(r.result().body + child().body, "")
    }

    def pageWithAwait: RenderResult =
      val r = melt.runtime.render.ServerRenderer()
      r.push("<main>")
      boundary(r, "main", nums())
      r.push("</main>")
      r.result()

    ctxWith(a).renderAsync(pageWithAwait).map { res =>
      assert(res.body.contains("<nav>1,2</nav>"), res.body)
      assert(res.body.contains("<main>1,2</main>"), res.body)
      assertEquals(calls.get(), 1)
    }
