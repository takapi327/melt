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
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given
import meltkit.adapter.http4s.Http4sMeltContext
import meltkit.codec.BodyDecoder
import org.http4s.{ Query as _, * }
import org.http4s.implicits.*

/** End-to-end test of streaming async SSR (`ctx.renderStream`) through the http4s
  * context. Mirrors what `<melt:await>` codegen emits (see [[RenderAsyncTest]]); the
  * streamed body is collected by compiling the response's fs2 stream to a String and
  * asserting the shell + swap bootstrap + per-boundary `<template>`/swap script + one
  * tail seed are all present. The final DOM after the client swap equals the blocking
  * [[RenderAsyncTest]] result — only the delivery differs.
  */
class RenderStreamTest extends CatsEffectSuite:

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

  /** Compiles a streaming response's fs2 body to a String (or returns the plain body). */
  private def collect(resp: meltkit.Response): IO[String] =
    resp match
      case StreamingResponse(_, _, b: Http4sAdapter.Fs2StreamBody[IO] @unchecked, _, _) =>
        b.toStream.through(fs2.text.utf8.decode).compile.string
      case other => IO.pure(other.body)

  test("renderStream flushes the shell fallback, streams a swap chunk, and seeds once at the tail"):
    val app = MeltKit[IO]()
    app.serve(list) { (_, _) => IO.pure(List(1, 2, 3)) }
    val ctx = ctxWith(app)

    ctx.renderStream(awaitShell(list())).flatMap(collect).map { html =>
      // The shell (with the pending fallback + its markers) is streamed first —
      // both remain in the raw stream; the client swap removes them at runtime.
      assert(html.contains("<p class=\"loading\">Loading…</p>"), html)
      assert(html.contains("<!--melt:sb:"), html)
      // Client swap runtime + one chunk per boundary (<template> + swap call).
      assert(html.contains("window.__meltSwap"), html)
      assert(html.contains("<template id=\"melt:t:"), html)
      assert(html.contains("<ul><li>1</li><li>2</li><li>3</li></ul>"), html)
      // Exactly one merged hydration seed at the tail.
      assert(html.contains("data-melt-queries"), html)
      assert(html.contains("\"nums.list\\nnull\":[1,2,3]"), html)
      assertEquals(html.split("data-melt-queries").length - 1, 1, html)
    }

  test("a failing query streams its Failed branch (not a 500) and is not seeded"):
    val app = MeltKit[IO]()
    app.serve(list) { (_, _) => IO.raiseError(new RuntimeException("db down")) }
    val ctx = ctxWith(app)

    ctx.renderStream(awaitShell(list())).flatMap(collect).map { html =>
      assert(html.contains("<p class=\"error\">failed</p>"), html)
      assert(!html.contains("data-melt-queries"), html)
    }

  test("a shell with no boundary degrades to a single non-streaming response"):
    val app = MeltKit[IO]()
    val ctx = ctxWith(app)

    ctx
      .renderStream {
        val r = ServerRenderer()
        r.push("<main>static</main>")
        r.result()
      }
      .flatMap(collect)
      .map { html =>
        assert(html.contains("<main>static</main>"), html)
        assert(!html.contains("__meltSwap"), html)
      }
