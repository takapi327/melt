/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.{ Files, Path }

import scala.concurrent.{ ExecutionContext, Future }

import melt.runtime.render.{ RenderResult, ServerRenderer }
import melt.runtime.Async

import meltkit.*

class SsgGeneratorSpec extends munit.FunSuite:

  val simpleTemplate: Template =
    Template.fromString("<html><body>%melt.body%</body></html>")

  val slug = param[String]("slug")

  def withTempDir(f: Path => Unit): Unit =
    val dir = Files.createTempDirectory("ssg-test-")
    try f(dir)
    finally deleteDirectory(dir)

  def deleteDirectory(dir: Path): Unit =
    val stream = Files.walk(dir)
    try stream.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
    finally stream.close()

  def config(out: Path): ServerConfig = ServerConfig(
    template  = simpleTemplate,
    outputDir = Some(out.toString)
  )

  val On = PageOptions(prerender = PrerenderOption.On)

  // ── normalizePath (tested via file output paths) ──────────────────────────

  test("/ is written to index.html"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("", On)(ctx => ctx.render(RenderResult(body = "<p>home</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("index.html")))
    }

  test("/about is written to about/index.html"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("about/index.html")))
    }

  test("/feed.xml is written to feed.xml (preserves extension)"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("feed.xml", On)(ctx => ctx.text("<rss/>"))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("feed.xml")))
    }

  // ── HTML content ──────────────────────────────────────────────────────────

  test("generated HTML wraps component body in template"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("about", On)(ctx => ctx.render(RenderResult(body = "<h1>About</h1>", head = "")))
      SsgGenerator.run(app, config(out))
      val html = Files.readString(out.resolve("about/index.html"))
      assert(html.contains("<h1>About</h1>"))
      assert(html.startsWith("<html>"))
    }

  // ── Dynamic routes ────────────────────────────────────────────────────────

  test("dynamic route /posts/:slug generates one file per entry"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get(
        "posts" / slug,
        PageOptions(prerender = PrerenderOption.On, entries = List("/posts/hello", "/posts/world"))
      )(ctx => ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("posts/hello/index.html")))
      assert(Files.exists(out.resolve("posts/world/index.html")))
    }

  test("dynamic route injects path parameter value into rendered HTML"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get(
        "posts" / slug,
        PageOptions(prerender = PrerenderOption.On, entries = List("/posts/scala"))
      )(ctx => ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = "")))
      SsgGenerator.run(app, config(out))
      val html = Files.readString(out.resolve("posts/scala/index.html"))
      assert(html.contains("<p>scala</p>"))
    }

  // ── prerender = Off ───────────────────────────────────────────────────────

  test("route without prerender = On is not generated"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("about")(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(!Files.exists(out.resolve("about/index.html")))
    }

  // ── Wildcard exclusion ────────────────────────────────────────────────────

  test("getAll (wildcard) route is never prerendered"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.getAll { ctx => ctx.render(RenderResult(body = "<p>wildcard</p>", head = "")) }
      app.get("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out))
      val html = Files.readString(out.resolve("about/index.html"))
      assert(html.contains("<p>about</p>"))
    }

  // ── Non-HTML / empty-body responses ──────────────────────────────────────

  test("ok response (JSON) does not create a file"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("api", On)(ctx => ctx.ok("ignored"))
      SsgGenerator.run(app, config(out))
      assert(!Files.exists(out.resolve("api/index.html")))
    }

  test("notFound response does not create a file"):
    withTempDir { out =>
      val app = MeltKit[[A] =>> A]()
      app.get("gone", On)(ctx => ctx.notFound())
      SsgGenerator.run(app, config(out))
      assert(!Files.exists(out.resolve("gone/index.html")))
    }

  // ── cleanOutput ───────────────────────────────────────────────────────────

  test("cleanOutput = true removes existing files before generation"):
    withTempDir { out =>
      val stale = out.resolve("stale.html")
      Files.writeString(stale, "old content")
      val app = MeltKit[[A] =>> A]()
      app.get("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out).copy(cleanOutput = true))
      assert(!Files.exists(stale))
    }

  test("cleanOutput = false keeps existing files"):
    withTempDir { out =>
      val stale = out.resolve("stale.html")
      Files.writeString(stale, "old content")
      val app = MeltKit[[A] =>> A]()
      app.get("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out).copy(cleanOutput = false))
      assert(Files.exists(stale))
    }

  test("<melt:await> is resolved at build time via ctx.renderAsync"):
    withTempDir { out =>
      // Same-thread EC so SyncRunner[Future] sees each Future already completed (the
      // SSG contract). A real IO-based SSG blocks on unsafeRunSync instead.
      given ExecutionContext = ExecutionContext.parasitic
      val nums               = ServerFn.query[Unit, List[Int]]("nums.list")
      val app                = MeltKit[Future]()
      app.serve(nums)((_, _) => Future.successful(List(1, 2, 3)))
      // A handler that renders a <melt:await> boundary (mirroring generated code)
      // and renders it with renderAsync, so SSG resolves the query at build time.
      app.get("nums", On) { ctx =>
        ctx.renderAsync {
          val r  = ServerRenderer()
          val id = SsrRenderScope.current.map(_.nextId()).getOrElse("melt-sb-0")
          r.push("<main><!--melt:sb:" + id + "-->")
          r.push("<p>Loading…</p>")
          r.push("<!--/melt:sb:" + id + "--></main>")
          SsrRenderScope.current.foreach(
            _.suspend(
              id,
              nums(),
              {
                case Async.Done(xs) => RenderResult("<ul>" + xs.map(n => s"<li>$n</li>").mkString + "</ul>", "")
                case _              => RenderResult("", "")
              }
            )
          )
          r.result()
        }
      }
      SsgGenerator.run(app, config(out))
      val html = Files.readString(out.resolve("nums/index.html"))
      assert(html.contains("<ul><li>1</li><li>2</li><li>3</li></ul>"), html) // resolved branch baked in
      assert(!html.contains("Loading…"), html)                               // marker + pending replaced
      assert(html.contains("data-melt-queries"), html)                       // seed injected for hydration
    }
