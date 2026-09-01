/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp.test

import java.io.File
import java.nio.file.Files

import zio.*
import zio.http.{ Body, Header, Request, Response as ZResponse, Status, URL }
import zio.test.*

import melt.runtime.json.PropsCodec
import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.adapter.ziohttp.ZioHttpAdapter
import meltkit.adapter.ziohttp.ZioInstances.given
import meltkit.codec.FormDataDecoder

/** Form actions (`app.page`) over the zio-http adapter.
  *
  * A page serves both a native form POST (JS off — the action re-renders or redirects) and a
  * `use:enhance` fetch (JS on — the same action returns a JSON envelope, selected by the
  * `x-melt-enhance` header). Neither path is adapter-specific: `FormDataDecoder` parses the
  * urlencoded body and `PropsCodec` writes the envelope, so this spec is checking that the
  * transport carries them intact.
  */
object FormActionSpec extends ZIOSpecDefault:

  private case class LoginForm(email: String, password: String, errors: List[String] = Nil)
    derives FormDataDecoder,
            PropsCodec

  private type App[A] = ZIO[Any, Throwable, A]

  private def withDist[A](f: File => ZIO[Any, Throwable, A]): ZIO[Any, Throwable, A] =
    ZIO.acquireReleaseWith(
      ZIO.attempt {
        val dir = Files.createTempDirectory("melt-zio-form").toFile
        Files.writeString(
          new File(dir, "index.html").toPath,
          """<!doctype html><html lang="%melt.lang%"><head>%melt.head%</head><body>%melt.body%</body></html>"""
        )
        dir
      }
    )(dir => ZIO.attempt(dir.listFiles.foreach(_.delete())).ignore *> ZIO.attempt(dir.delete()).ignore)(f)

  /** One form page with a default action and one named action. */
  private def app: MeltKit[App] =
    val a = MeltKit[App]()

    a.page("login")(
      render = (_, form: Option[LoginForm]) =>
        val errors = form.toList.flatMap(_.errors).map(e => s"<p class=\"error\">$e</p>").mkString
        RenderResult(body = s"<form method=\"post\">$errors</form>", head = ""),
      action = ctx =>
        ctx.body.form[LoginForm].map {
          case Right(f) if !f.email.contains("@") =>
            fail(422, f.copy(errors = List("Enter a valid email address")))
          case Right(_)  => ActionResult.Redirect("/dashboard")
          case Left(err) => fail(400, LoginForm("", "", errors = List(err.message)))
        }
    )

    a.page("posts")(
      render = (_, form: Option[LoginForm]) => RenderResult(body = "<form method=\"post\"></form>", head = ""),
      actions = {
        case ("save", ctx)    => ctx.body.form[LoginForm].map(_ => ActionResult.Redirect("/result/draft"))
        case ("publish", ctx) => ctx.body.form[LoginForm].map(_ => ActionResult.Redirect("/result/published"))
      }
    )

    a

  private final case class Out(status: Status, body: String, headers: Map[String, String]):
    def header(name: String): Option[String] = headers.get(name.toLowerCase)

  private def run(routes: zio.http.Routes[Any, ZResponse], request: Request): ZIO[Any, Throwable, Out] =
    ZIO.scoped {
      routes.runZIO(request).flatMap { res =>
        res.body.asString.map { body =>
          Out(res.status, body, res.headers.map(h => h.headerName.toLowerCase -> h.renderedValue).toMap)
        }
      }
    }

  private def post(path: String, form: String, enhance: Boolean = false): ZIO[Any, Throwable, Out] =
    withDist { dist =>
      val base = Request
        .post(URL.decode(path).toOption.get, Body.fromString(form))
        .addHeader("Content-Type", "application/x-www-form-urlencoded")
      val request = if enhance then base.addHeader("x-melt-enhance", "true") else base
      ZioHttpAdapter.ssrRoutes(app, dist, ViteManifest.empty).flatMap(run(_, request))
    }

  def spec = suite("form actions over zio-http")(
    test("GET renders the page with no form state") {
      withDist { dist =>
        ZioHttpAdapter
          .ssrRoutes(app, dist, ViteManifest.empty)
          .flatMap(run(_, Request.get(URL.decode("/login").toOption.get)))
          .map(o => assertTrue(o.status == Status.Ok, o.body.contains("<form"), !o.body.contains("class=\"error\"")))
      }
    },
    test("a native POST that fails validation re-renders the page with the error") {
      post("/login", "email=nope&password=secret").map { o =>
        assertTrue(o.status == Status.UnprocessableEntity, o.body.contains("Enter a valid email address"))
      }
    },
    test("a native POST that succeeds redirects") {
      post("/login", "email=a@b.com&password=secret").map { o =>
        assertTrue(o.status == Status.SeeOther, o.header("location").contains("/dashboard"))
      }
    },
    test("use:enhance gets a JSON failure envelope instead of HTML") {
      // The same action, selected by the `x-melt-enhance` header — the client patches the form
      // in place rather than replacing the document.
      post("/login", "email=nope&password=secret", enhance = true).map { o =>
        assertTrue(
          o.body.contains("\"type\":\"failure\""),
          o.body.contains("\"status\":422"),
          o.body.contains("Enter a valid email address")
        )
      }
    },
    test("use:enhance gets a JSON redirect envelope on success") {
      post("/login", "email=a@b.com&password=secret", enhance = true).map { o =>
        assertTrue(o.body.contains("\"type\":\"redirect\""), o.body.contains("\"location\":\"/dashboard\""))
      }
    },
    test("named actions dispatch on the ?/name query") {
      for
        save    <- post("/posts?/save", "email=a@b.com&password=secret")
        publish <- post("/posts?/publish", "email=a@b.com&password=secret")
      yield assertTrue(
        save.header("location").contains("/result/draft"),
        publish.header("location").contains("/result/published")
      )
    },
    test("a malformed body is a 400 carrying the decode error") {
      post("/login", "email=nope").map { o =>
        assertTrue(o.status == Status.BadRequest, o.body.contains("password"))
      }
    },
    test("the CSRF hook rejects a cross-origin form POST") {
      // `ServerHook.csrf` guards state-changing submits by checking `Origin`; it runs through the
      // adapter's hook chain, so a missing or foreign origin must never reach the action.
      val guarded = MeltKit[App]()
      guarded.use(ServerHook.csrf[App]())
      guarded.page("login")(
        render = (_, form: Option[LoginForm]) => RenderResult(body = "<form method=\"post\"></form>", head = ""),
        action = ctx => ctx.body.form[LoginForm].map(_ => ActionResult.Redirect("/dashboard"))
      )

      withDist { dist =>
        def submit(origin: Option[String]) =
          val base = Request
            .post(URL.decode("/login").toOption.get, Body.fromString("email=a@b.com&password=secret"))
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
          val request = origin.fold(base)(o => base.addHeader("Origin", o))
          ZioHttpAdapter.ssrRoutes(guarded, dist, ViteManifest.empty).flatMap(run(_, request))

        for
          none    <- submit(None)
          foreign <- submit(Some("https://evil.example"))
        yield assertTrue(none.status == Status.Forbidden, foreign.status == Status.Forbidden)
      }
    }
  )
