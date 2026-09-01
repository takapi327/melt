/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import zio.*
import zio.test.*

import melt.runtime.render.{ RenderResult, ServerRenderer }
import melt.runtime.Async

import meltkit.*
import meltkit.adapter.ziohttp.{ ZioHttpMeltContext, ZStreamBody }
import meltkit.adapter.ziohttp.ZioInstances.given
import meltkit.codec.BodyDecoder

/** Async and streaming SSR (`<melt:await>`) through the zio-http context.
  *
  * The shell built by [[awaitShell]] mirrors what `<melt:await>` codegen emits: a boundary
  * marker around a pending fallback, plus a suspension registered on the render scope. Both
  * entry points must end at the same DOM — `renderAsync` splices the resolved branch into one
  * response, `renderStream` flushes the shell first and streams the swap — so the assertions
  * compare what each actually produces.
  */
object RenderStreamSpec extends ZIOSpecDefault:

  private type App[A] = ZIO[Any, Throwable, A]

  private val list = ServerFn.query[Unit, List[Int]]("nums.list")

  private val template =
    Template.fromString("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")

  private def ctxWith(app: MeltKit[App]): ZioHttpMeltContext[Any, PathSpec.Empty, Unit] =
    new ZioHttpMeltContext[Any, PathSpec.Empty, Unit](
      PathSpec.emptyValue,
      zio.http.Request.get(zio.http.URL.decode("/await").toOption.get),
      summon[BodyDecoder[Unit]],
      new Locals(),
      Some(template),
      ViteManifest.empty,
      "en",
      "",
      None,
      Some(app),
      None
    )

  /** The markup `<melt:await>` lowers to: a marked boundary holding a pending fallback, with the
    * resolved branch registered on the scope. */
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

  /** Drains a streamed response into a String; a plain response returns its body as-is. */
  private def collect(res: Response): ZIO[Any, Throwable, String] =
    res match
      case StreamingResponse(_, _, b: ZStreamBody, _, _) =>
        b.stream.runCollect.map(bytes => new String(bytes.toArray, "UTF-8"))
      case other => ZIO.succeed(other.body)

  private def appWithList: MeltKit[App] =
    val app = MeltKit[App]()
    app.serve(list) { (_, _) => ZIO.succeed(List(1, 2, 3)) }
    app

  def spec = suite("async / streaming SSR over zio-http")(
    test("renderAsync splices the resolved branch into a single response") {
      val ctx = ctxWith(appWithList)
      ctx.renderAsync(awaitShell(list())).flatMap(collect).map { html =>
        assertTrue(
          html.contains("<li>1</li><li>2</li><li>3</li>"),
          // The pending fallback is replaced, not appended.
          !html.contains("class=\"loading\"")
        )
      }
    },
    test("renderStream carries a ZStreamBody, not a plain string body") {
      // A `StreamingResponse` that fell back to a String body would silently lose the
      // incremental flush while still looking correct in the collected output.
      val ctx = ctxWith(appWithList)
      ctx.renderStream(awaitShell(list())).map { res =>
        assertTrue(res.isInstanceOf[StreamingResponse], res.asInstanceOf[StreamingResponse].stream.isInstanceOf[ZStreamBody])
      }
    },
    test("renderStream flushes the shell first, then the swap chunk, then one tail seed") {
      val ctx = ctxWith(appWithList)
      ctx.renderStream(awaitShell(list())).flatMap(collect).map { html =>
        val shellAt = html.indexOf("class=\"loading\"")
        val swapAt  = html.indexOf("<li>1</li>")
        assertTrue(
          // The shell with its pending fallback goes out before anything resolves.
          shellAt >= 0,
          swapAt > shellAt,
          html.contains("<li>1</li><li>2</li><li>3</li>"),
          // The client swaps by id, so the boundary marker has to survive into the stream.
          html.contains("melt:sb:")
        )
      }
    },
    test("a page with no boundary is sent whole rather than chunked") {
      val ctx = ctxWith(MeltKit[App]())
      for
        async  <- ctx.renderAsync(RenderResult(body = "<h1>plain</h1>", head = ""))
        stream <- ctx.renderStream(RenderResult(body = "<h1>plain</h1>", head = ""))
        a      <- collect(async)
        s      <- collect(stream)
      yield assertTrue(
        !async.isInstanceOf[StreamingResponse],
        !stream.isInstanceOf[StreamingResponse],
        a.contains("<h1>plain</h1>"),
        s.contains("<h1>plain</h1>")
      )
    },
    test("a failing boundary renders its error branch instead of dropping the response") {
      val app = MeltKit[App]()
      app.serve(list) { (_, _) => ZIO.fail(new RuntimeException("upstream down")) }
      val ctx = ctxWith(app)
      ctx.renderStream(awaitShell(list())).flatMap(collect).map { html =>
        assertTrue(html.contains("class=\"error\""))
      }
    }
  )
