/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.{ Files, Path }

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.ssg.SsgRunner.given

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

  def config(out: Path): SsgConfig = SsgConfig(out, simpleTemplate)

  val On = PageOptions(prerender = PrerenderOption.On)

  // ── normalizePath (tested via file output paths) ──────────────────────────

  test("/ is written to index.html"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page("", On)(ctx => ctx.render(RenderResult(body = "<p>home</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("index.html")))
    }

  test("/about is written to about/index.html"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("about/index.html")))
    }

  test("/feed.xml is written to feed.xml (preserves extension)"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page("feed.xml", On)(ctx => ctx.text("<rss/>"))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("feed.xml")))
    }

  // ── HTML content ──────────────────────────────────────────────────────────

  test("generated HTML wraps component body in template"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page("about", On)(ctx => ctx.render(RenderResult(body = "<h1>About</h1>", head = "")))
      SsgGenerator.run(app, config(out))
      val html = Files.readString(out.resolve("about/index.html"))
      assert(html.contains("<h1>About</h1>"))
      assert(html.startsWith("<html>"))
    }

  // ── Dynamic routes ────────────────────────────────────────────────────────

  test("dynamic route /posts/:slug generates one file per entry"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page(
          "posts" / slug,
          PageOptions(prerender = PrerenderOption.On, entries = List("/posts/hello", "/posts/world"))
        )(ctx => ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(Files.exists(out.resolve("posts/hello/index.html")))
      assert(Files.exists(out.resolve("posts/world/index.html")))
    }

  test("dynamic route injects path parameter value into rendered HTML"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page(
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
      val app = new MeltApp[[A] =>> A]:
        page("about")(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out))
      assert(!Files.exists(out.resolve("about/index.html")))
    }

  // ── Wildcard exclusion ────────────────────────────────────────────────────

  test("getAll (wildcard) route is never prerendered"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        getAll { ctx => ctx.render(RenderResult(body = "<p>wildcard</p>", head = "")) }
        page("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out))
      val html = Files.readString(out.resolve("about/index.html"))
      assert(html.contains("<p>about</p>"))
    }

  // ── Non-HTML / empty-body responses ──────────────────────────────────────

  test("ok response (empty body in SSG) does not create a file"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page("api", On)(ctx => ctx.ok("ignored"))
      SsgGenerator.run(app, config(out))
      assert(!Files.exists(out.resolve("api/index.html")))
    }

  test("notFound response does not create a file"):
    withTempDir { out =>
      val app = new MeltApp[[A] =>> A]:
        page("gone", On)(ctx => ctx.notFound())
      SsgGenerator.run(app, config(out))
      assert(!Files.exists(out.resolve("gone/index.html")))
    }

  // ── cleanOutput ───────────────────────────────────────────────────────────

  test("cleanOutput = true removes existing files before generation"):
    withTempDir { out =>
      val stale = out.resolve("stale.html")
      Files.writeString(stale, "old content")
      val app = new MeltApp[[A] =>> A]:
        page("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out).copy(cleanOutput = true))
      assert(!Files.exists(stale))
    }

  test("cleanOutput = false keeps existing files"):
    withTempDir { out =>
      val stale = out.resolve("stale.html")
      Files.writeString(stale, "old content")
      val app = new MeltApp[[A] =>> A]:
        page("about", On)(ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")))
      SsgGenerator.run(app, config(out).copy(cleanOutput = false))
      assert(Files.exists(stale))
    }
