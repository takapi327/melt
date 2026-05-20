/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.concurrent.{ ExecutionContext, Future }
import scala.scalajs.concurrent.JSExecutionContext
import scala.scalajs.js
import scala.scalajs.js.annotation.*

import melt.runtime.render.RenderResult

@js.native @JSImport("os", JSImport.Namespace)
private object NodeOs extends js.Object:
  def tmpdir(): String = js.native

import meltkit.*

class NodeSsgGeneratorSpec extends munit.FunSuite:

  given ExecutionContext = JSExecutionContext.Implicits.queue

  val simpleTemplate: Template =
    Template.fromString("<html><body>%melt.body%</body></html>")

  val slug = param[String]("slug")

  def withTempDir(f: String => Unit): Unit =
    val dir = NodeFsSsg.mkdtempSync(NodePath.join(NodeOs.tmpdir(), "ssg-test-"))
    try f(dir)
    finally NodeFsSsg.rmSync(dir, js.Dynamic.literal(recursive = true, force = true))

  def config(out: String): ServerConfig = ServerConfig(
    template  = simpleTemplate,
    outputDir = Some(out)
  )

  val On = PageOptions(prerender = PrerenderOption.On)

  // ── normalizePath (tested via file output paths) ──────────────────────────

  test("/ is written to index.html"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("", On)(ctx => Future.successful(ctx.render(RenderResult(body = "<p>home</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      assert(NodeFsSsg.existsSync(NodePath.join(out, "index.html")))
    }

  test("/about is written to about/index.html"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("about", On)(ctx => Future.successful(ctx.render(RenderResult(body = "<p>about</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      assert(NodeFsSsg.existsSync(NodePath.join(out, "about/index.html")))
    }

  test("/feed.xml is written to feed.xml (preserves extension)"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("feed.xml", On)(ctx => Future.successful(ctx.text("<rss/>")))
      NodeSsgGenerator.run(app, config(out))
      assert(NodeFsSsg.existsSync(NodePath.join(out, "feed.xml")))
    }

  // ── HTML content ──────────────────────────────────────────────────────────

  test("generated HTML wraps component body in template"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("about", On)(ctx => Future.successful(ctx.render(RenderResult(body = "<h1>About</h1>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      val html = NodeFsSsg.readFileSync(NodePath.join(out, "about/index.html"), "utf8")
      assert(html.contains("<h1>About</h1>"))
      assert(html.startsWith("<html>"))
    }

  // ── Dynamic routes ────────────────────────────────────────────────────────

  test("dynamic route /posts/:slug generates one file per entry"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get(
        "posts" / slug,
        PageOptions(prerender = PrerenderOption.On, entries = List("/posts/hello", "/posts/world"))
      )(ctx => Future.successful(ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      assert(NodeFsSsg.existsSync(NodePath.join(out, "posts/hello/index.html")))
      assert(NodeFsSsg.existsSync(NodePath.join(out, "posts/world/index.html")))
    }

  test("dynamic route injects path parameter value into rendered HTML"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get(
        "posts" / slug,
        PageOptions(prerender = PrerenderOption.On, entries = List("/posts/scala"))
      )(ctx => Future.successful(ctx.render(RenderResult(body = s"<p>${ ctx.params.slug }</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      val html = NodeFsSsg.readFileSync(NodePath.join(out, "posts/scala/index.html"), "utf8")
      assert(html.contains("<p>scala</p>"))
    }

  // ── prerender = Off ───────────────────────────────────────────────────────

  test("route without prerender = On is not generated"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("about")(ctx => Future.successful(ctx.render(RenderResult(body = "<p>about</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      assert(!NodeFsSsg.existsSync(NodePath.join(out, "about/index.html")))
    }

  // ── Wildcard exclusion ────────────────────────────────────────────────────

  test("getAll (wildcard) route is never prerendered"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.getAll { ctx => Future.successful(ctx.render(RenderResult(body = "<p>wildcard</p>", head = ""))) }
      app.get("about", On)(ctx => Future.successful(ctx.render(RenderResult(body = "<p>about</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out))
      val html = NodeFsSsg.readFileSync(NodePath.join(out, "about/index.html"), "utf8")
      assert(html.contains("<p>about</p>"))
    }

  // ── Non-HTML / empty-body responses ──────────────────────────────────────

  test("ok response (JSON) does not create a file"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("api", On)(ctx => Future.successful(ctx.ok("ignored")))
      NodeSsgGenerator.run(app, config(out))
      assert(!NodeFsSsg.existsSync(NodePath.join(out, "api/index.html")))
    }

  test("notFound response does not create a file"):
    withTempDir { out =>
      val app = MeltKit[Future]()
      app.get("gone", On)(ctx => Future.successful(ctx.notFound()))
      NodeSsgGenerator.run(app, config(out))
      assert(!NodeFsSsg.existsSync(NodePath.join(out, "gone/index.html")))
    }

  // ── cleanOutput ───────────────────────────────────────────────────────────

  test("cleanOutput = true removes existing files before generation"):
    withTempDir { out =>
      val stale = NodePath.join(out, "stale.html")
      NodeFsSsg.writeFileSync(stale, "old content", "utf8")
      val app = MeltKit[Future]()
      app.get("about", On)(ctx => Future.successful(ctx.render(RenderResult(body = "<p>about</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out).copy(cleanOutput = true))
      assert(!NodeFsSsg.existsSync(stale))
    }

  test("cleanOutput = false keeps existing files"):
    withTempDir { out =>
      val stale = NodePath.join(out, "stale.html")
      NodeFsSsg.writeFileSync(stale, "old content", "utf8")
      val app = MeltKit[Future]()
      app.get("about", On)(ctx => Future.successful(ctx.render(RenderResult(body = "<p>about</p>", head = ""))))
      NodeSsgGenerator.run(app, config(out).copy(cleanOutput = false))
      assert(NodeFsSsg.existsSync(stale))
    }
