/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.{ Files, Path }

import melt.runtime.render.RenderResult

import meltkit.{ param, MeltKit, SyncRunner, Template, ViteManifest }

class SsgGeneratorSpec extends munit.FunSuite:

  type Id = [A] =>> A

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

  def makeSsgApp(routes: MeltKit[Id] => Unit, pathList: List[String]): SsgApp[Id] =
    val k = new MeltKit[Id]
    routes(k)
    new SsgApp[Id]:
      override val kit:      MeltKit[Id]  = k
      override val paths:    List[String] = pathList
      override val template: Template     = simpleTemplate
      override val manifest: ViteManifest = ViteManifest.empty
      override given syncRunner: SyncRunner[Id] with
        override def runSync[A](fa: A): A = fa

  // ── normalizePath (tested via file output paths) ──────────────────────────

  test("/ is written to index.html"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("") { ctx => ctx.render(RenderResult(body = "<p>home</p>", head = "")) },
        List("/")
      )
      app.generate(SsgConfig(out))
      assert(Files.exists(out.resolve("index.html")))
    }

  test("/about is written to about/index.html"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("about") { ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")) },
        List("/about")
      )
      app.generate(SsgConfig(out))
      assert(Files.exists(out.resolve("about/index.html")))
    }

  test("/feed.xml is written to feed.xml (preserves extension)"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("feed.xml") { ctx => ctx.text("<rss/>") },
        List("/feed.xml")
      )
      app.generate(SsgConfig(out))
      assert(Files.exists(out.resolve("feed.xml")))
    }

  // ── HTML content ──────────────────────────────────────────────────────────

  test("generated HTML wraps component body in template"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("about") { ctx =>
          ctx.render(RenderResult(body = "<h1>About</h1>", head = ""))
        },
        List("/about")
      )
      app.generate(SsgConfig(out))
      val html = Files.readString(out.resolve("about/index.html"))
      assert(html.contains("<h1>About</h1>"))
      assert(html.startsWith("<html>"))
    }

  // ── Dynamic routes ────────────────────────────────────────────────────────

  test("dynamic route /posts/:slug generates one file per path"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("posts" / slug) { ctx =>
          ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = ""))
        },
        List("/posts/hello", "/posts/world")
      )
      app.generate(SsgConfig(out))
      assert(Files.exists(out.resolve("posts/hello/index.html")))
      assert(Files.exists(out.resolve("posts/world/index.html")))
    }

  test("dynamic route injects path parameter value into rendered HTML"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("posts" / slug) { ctx =>
          ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = ""))
        },
        List("/posts/scala")
      )
      app.generate(SsgConfig(out))
      val html = Files.readString(out.resolve("posts/scala/index.html"))
      assert(html.contains("<p>scala</p>"))
    }

  // ── Unmatched route ───────────────────────────────────────────────────────

  test("unmatched path does not create a file"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("home") { ctx => ctx.render(RenderResult(body = "<p>home</p>", head = "")) },
        List("/missing")
      )
      app.generate(SsgConfig(out))
      assert(!Files.exists(out.resolve("missing/index.html")))
    }

  // ── Wildcard exclusion ────────────────────────────────────────────────────

  test("wildcard (getAll) route is excluded; specific route takes precedence"):
    withTempDir { out =>
      val app = makeSsgApp(
        kit =>
          kit.getAll { ctx => ctx.render(RenderResult(body = "<p>wildcard</p>", head = "")) }
          kit.get("about") { ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")) }
        ,
        List("/about")
      )
      app.generate(SsgConfig(out))
      val html = Files.readString(out.resolve("about/index.html"))
      assert(html.contains("<p>about</p>"), "specific route should be used, not wildcard")
    }

  test("wildcard-only app does not generate any files"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.getAll { ctx => ctx.render(RenderResult(body = "<p>wildcard</p>", head = "")) },
        List("/about")
      )
      app.generate(SsgConfig(out))
      assert(!Files.exists(out.resolve("about/index.html")))
    }

  // ── Non-HTML / empty-body responses ──────────────────────────────────────

  test("ok response (empty body in SSG) does not create a file"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("api") { ctx => ctx.ok("ignored") },
        List("/api")
      )
      app.generate(SsgConfig(out))
      assert(!Files.exists(out.resolve("api/index.html")))
    }

  test("notFound response does not create a file"):
    withTempDir { out =>
      val app = makeSsgApp(
        _.get("gone") { ctx => ctx.notFound() },
        List("/gone")
      )
      app.generate(SsgConfig(out))
      assert(!Files.exists(out.resolve("gone/index.html")))
    }

  // ── cleanOutput ───────────────────────────────────────────────────────────

  test("cleanOutput = true removes existing files before generation"):
    withTempDir { out =>
      val stale = out.resolve("stale.html")
      Files.writeString(stale, "old content")
      val app = makeSsgApp(
        _.get("about") { ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")) },
        List("/about")
      )
      app.generate(SsgConfig(outputDir = out, cleanOutput = true))
      assert(!Files.exists(stale))
    }

  test("cleanOutput = false keeps existing files"):
    withTempDir { out =>
      val stale = out.resolve("stale.html")
      Files.writeString(stale, "old content")
      val app = makeSsgApp(
        _.get("about") { ctx => ctx.render(RenderResult(body = "<p>about</p>", head = "")) },
        List("/about")
      )
      app.generate(SsgConfig(outputDir = out, cleanOutput = false))
      assert(Files.exists(stale))
    }
