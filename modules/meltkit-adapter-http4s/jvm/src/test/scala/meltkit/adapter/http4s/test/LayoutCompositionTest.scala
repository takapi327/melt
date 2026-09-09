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

  /** Compiles a streamed response body so its boundaries actually resolve. */
  private def drain(resp: meltkit.Response): IO[String] =
    resp match
      case StreamingResponse(_, _, b: meltkit.adapter.http4s.Http4sAdapter.Fs2StreamBody[IO] @unchecked, _, _) =>
        b.toStream.through(fs2.text.utf8.decode).compile.string
      case other => IO.pure(other.body)

  /** A boundary awaiting `q`, labelled, for the dedup cases below. */
  private def boundaryOf(r: melt.runtime.render.ServerRenderer, label: String, q: meltkit.Query[Int]): Unit =
    val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
    r.push("<!--melt:sb:" + id + "-->")
    r.push("<i></i>")
    r.push("<!--/melt:sb:" + id + "-->")
    SsrRenderScope.current.foreach(
      _.suspend(
        id,
        q,
        {
          case melt.runtime.Async.Done(v)   => RenderResult(s"<$label>$v</$label>", "")
          case melt.runtime.Async.Failed(_) => RenderResult(s"<$label-err/>", "")
          case melt.runtime.Async.Loading   => RenderResult(s"<$label-load/>", "")
        }
      )
    )

  test("the same query with different arguments is not merged"):
    val twice = ServerFn.query[Int, Int]("dedup.twice")
    val seen  = scala.collection.mutable.ListBuffer.empty[Int]

    val a = MeltKit[IO]()
    a.serve(twice) { (in, _) => IO { seen += in; in * 10 } }
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "one", twice(1))
      RenderResult(r.result().body + child().body, "")
    }

    def page: RenderResult =
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "two", twice(2))
      r.result()

    ctxWith(a).renderAsync(page).map { res =>
      assertEquals(seen.toList.sorted, List(1, 2))
      assert(res.body.contains("<one>10</one>"), res.body)
      assert(res.body.contains("<two>20</two>"), res.body)
    }

  test("a shared query that fails renders the error branch in every boundary"):
    val boom  = ServerFn.query[Int, Int]("dedup.boom")
    val calls = new java.util.concurrent.atomic.AtomicInteger(0)

    val a = MeltKit[IO]()
    a.serve(boom) { (_, _) => IO(calls.incrementAndGet()) *> IO.raiseError(new RuntimeException("down")) }
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "nav", boom(1))
      RenderResult(r.result().body + child().body, "")
    }

    def page: RenderResult =
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "main", boom(1))
      r.result()

    ctxWith(a).renderAsync(page).map { res =>
      assertEquals(calls.get(), 1)
      assert(res.body.contains("<nav-err/>"), res.body)
      assert(res.body.contains("<main-err/>"), res.body)
      assert(!res.body.contains("data-melt-queries"), res.body)
    }

  test("a shared query nobody served renders the loading branch in every boundary"):
    val absent = ServerFn.query[Int, Int]("dedup.absent")

    val a = MeltKit[IO]()
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "nav", absent(1))
      RenderResult(r.result().body + child().body, "")
    }

    def page: RenderResult =
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "main", absent(1))
      r.result()

    ctxWith(a).renderAsync(page).map { res =>
      assert(res.body.contains("<nav-load/>"), res.body)
      assert(res.body.contains("<main-load/>"), res.body)
    }

  test("streaming resolves each top-level boundary in its own scope, so a shared query runs per chunk"):
    val shared = ServerFn.query[Int, Int]("dedup.streamed")
    val calls  = new java.util.concurrent.atomic.AtomicInteger(0)

    val a = MeltKit[IO]()
    a.serve(shared) { (in, _) => IO(calls.incrementAndGet()).as(in) }
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "nav", shared(1))
      RenderResult(r.result().body + child().body, "")
    }

    def page: RenderResult =
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "main", shared(1))
      r.result()

    ctxWith(a).renderStream(page).flatMap(drain).map { _ =>
      assertEquals(calls.get(), 2)
    }

  test("ctx.renderPage composes the registered layout"):
    val html = ctxWith(appWithLayout).renderPage(page).body
    assert(html.contains("<shell><p>page</p></shell>"), html)

  test("ctx.render leaves a boundary for the client instead of resolving it"):
    val nums = ServerFn.query[Int, Int]("plain.nums")
    val a    = MeltKit[IO]()
    a.serve(nums) { (in, _) => IO.pure(in * 10) }
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "nav", nums(4))
      RenderResult(r.result().body + child().body, "")
    }

    val html = ctxWith(a).render(page).body
    assert(html.contains("melt:sb:"), html)
    assert(!html.contains("<nav>40</nav>"), html)
    assert(!html.contains("data-melt-queries"), html)

  test("ctx.renderAsync is what resolves that same boundary"):
    val nums = ServerFn.query[Int, Int]("plain.nums.async")
    val a    = MeltKit[IO]()
    a.serve(nums) { (in, _) => IO.pure(in * 10) }
    a.layout("") { child =>
      val r = melt.runtime.render.ServerRenderer()
      boundaryOf(r, "nav", nums(4))
      RenderResult(r.result().body + child().body, "")
    }

    ctxWith(a).renderAsync(page).map { res =>
      assert(res.body.contains("<nav>40</nav>"), res.body)
    }
