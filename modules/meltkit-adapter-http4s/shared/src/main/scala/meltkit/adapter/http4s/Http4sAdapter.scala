/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import cats.data.OptionT
import cats.effect.Async
import cats.effect.Concurrent
import cats.syntax.all.*
import fs2.io.file.Files
import fs2.io.file.Path
import meltkit.*
import meltkit.codec.BodyDecoder
import org.http4s.headers.`Content-Type`
import org.http4s.server.staticcontent.fileService
import org.http4s.server.staticcontent.FileService
import org.http4s.server.Router
import org.http4s.Charset
import org.http4s.Headers
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Response as Http4sResponse
import org.http4s.Status

/** Converts a [[MeltKit]] router into an http4s [[HttpRoutes]].
  *
  * Works on both JVM and Node.js (v18+). On JVM, `Router.withPath` uses
  * `ThreadLocal`; on Node.js it uses `AsyncLocalStorage` — resolved via the
  * platform-specific dependency configured in `build.sbt`.
  *
  * ==API-only==
  *
  * {{{
  * val app = MeltKit[IO]()
  * app.get("api" / "users") { ctx => IO.pure(ctx.json("[...]")) }
  *
  * val httpApp = Http4sAdapter.routes(app).orNotFound
  * }}}
  *
  * ==Full SPA (API + static assets + index.html catch-all)==
  *
  * {{{
  * import generated.AssetManifest
  *
  * val app = MeltKit[IO]()
  * // define routes ...
  *
  * val httpApp = Http4sAdapter.spaRoutes(app, AssetManifest.clientDistDir, AssetManifest.manifest)
  *   .map(_.orNotFound)
  * }}}
  *
  * ==SSR (server-side rendered pages via ctx.render())==
  *
  * `index.html` is read from `clientDistDir / "index.html"` at startup via
  * [[fs2.io.file.Files]], so this works on both JVM and Node.js.
  * `sbt-meltc` copies the template automatically when `meltcIndexHtml` is set.
  *
  * {{{
  * import generated.AssetManifest
  *
  * val app = MeltKit[IO]()
  * app.get("users" / userId) { ctx =>
  *   Database.findUser(ctx.params.userId).map(u => ctx.render(UserDetailPage(u)))
  * }
  *
  * val httpApp =
  *   Http4sAdapter(app, AssetManifest.clientDistDir, AssetManifest.manifest)
  *     .map(_.routes.orNotFound)
  * }}}
  */
final class Http4sAdapter[F[_]: Concurrent] private (
  private val app:      MeltKitPlatform[F, RenderResult],
  private val template: Template,
  private val manifest: ViteManifest,
  private val lang:     String,
  private val basePath: String
):

  /** Builds [[HttpRoutes]] from the [[MeltKit]] router with SSR support.
    *
    * Route handlers may call `ctx.render(result)` to render Melt components
    * server-side and return the resulting HTML response.
    */
  def routes: HttpRoutes[F] =
    HttpRoutes[F] { request =>
      val method   = HttpMethod.fromString(request.method.name)
      val segments = request.pathInfo.segments.toList.map(_.decoded())

      val matched = method.flatMap { m =>
        app.routes.find { r =>
          r.method == m && PathSegment.matches(r.segments, segments)
        }
      }

      matched match
        case None        => OptionT.none
        case Some(route) =>
          val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
          val factory   = new MeltContextFactory[F, RenderResult]:
            def build[P <: AnyNamedTuple, B](
              params:      P,
              bodyDecoder: BodyDecoder[B]
            ): MeltContext[F, P, B, RenderResult] =
              Http4sMeltContext(params, request, bodyDecoder, Some(template), manifest, lang, basePath)
          route.tryHandle(rawValues, factory) match
            case None         => OptionT.none
            case Some(effect) =>
              OptionT.liftF(
                effect
                  .map(Http4sAdapter.toHttp4sResponse[F])
                  .handleErrorWith {
                    case e: BodyDecodeException =>
                      Concurrent[F].pure(Http4sAdapter.toHttp4sResponse(Response.badRequest(e.error.message)))
                  }
              )
    }

object Http4sAdapter:

  /** Bridges [[cats.Functor]] to [[meltkit.Functor]] so that [[meltkit.ServerMeltKitPlatform.on]]
    * works with any `F` that has a cats `Functor` instance (e.g. `cats.effect.IO`).
    *
    * Import via:
    * {{{
    * import meltkit.adapter.http4s.Http4sAdapter.given
    * }}}
    */
  given [F[_]: cats.Functor]: meltkit.Functor[F] with
    override def map[A, B](fa: F[A])(f: A => B): F[B] = cats.Functor[F].map(fa)(f)

  /** Creates an [[Http4sAdapter]] for SSR rendering.
    *
    * Reads `index.html` from `clientDistDir / "index.html"` once at startup
    * via [[fs2.io.file.Files]], making this work on both JVM and Node.js.
    * Use `meltcIndexHtml` in `build.sbt` to have sbt-meltc copy the template
    * into `clientDistDir` automatically.
    *
    * @param app           the [[MeltKit]] router
    * @param clientDistDir directory containing `index.html` (usually `AssetManifest.clientDistDir`)
    * @param manifest      the asset manifest (usually `AssetManifest.manifest`)
    * @param lang          default HTML `lang` attribute (default `"en"`)
    * @param basePath      asset base path for [[Template.render]] (default `"/assets"`)
    */
  def apply[F[_]: Async: Files](
    app:           MeltKitPlatform[F, RenderResult],
    clientDistDir: Path,
    manifest:      ViteManifest,
    lang:          String = "en",
    basePath:      String = "/assets"
  ): F[Http4sAdapter[F]] =
    Files[F]
      .readAll(clientDistDir / "index.html")
      .through(fs2.text.utf8.decode)
      .compile
      .string
      .map(content => new Http4sAdapter(app, Template.fromString(content), manifest, lang, basePath))

  /** Builds [[HttpRoutes]] from a [[MeltKit]] router (API routes only).
    *
    * `ctx.render()` is **not** available in route handlers registered via this
    * method. For SSR rendering use `Http4sAdapter(app, template, manifest).routes`.
    * For a complete SPA setup use [[spaRoutes]].
    */
  def routes[F[_]: Concurrent](app: MeltKitPlatform[F, RenderResult]): HttpRoutes[F] =
    HttpRoutes[F] { request =>
      val method   = HttpMethod.fromString(request.method.name)
      val segments = request.pathInfo.segments.toList.map(_.decoded())

      val matched = method.flatMap { m =>
        app.routes.find { r =>
          r.method == m && PathSegment.matches(r.segments, segments)
        }
      }

      matched match
        case None        => OptionT.none
        case Some(route) =>
          val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
          val factory   = new MeltContextFactory[F, RenderResult]:
            def build[P <: AnyNamedTuple, B](
              params:      P,
              bodyDecoder: BodyDecoder[B]
            ): MeltContext[F, P, B, RenderResult] =
              Http4sMeltContext(params, request, bodyDecoder)
          route.tryHandle(rawValues, factory) match
            case None         => OptionT.none
            case Some(effect) =>
              OptionT.liftF(
                effect
                  .map(toHttp4sResponse[F])
                  .handleErrorWith {
                    case e: BodyDecodeException =>
                      Concurrent[F].pure(toHttp4sResponse(Response.badRequest(e.error.message)))
                  }
              )
    }

  /** Builds [[HttpRoutes]] for a full SPA setup:
    *
    *   - MeltKit routes (API endpoints)
    *   - `/assets/...` → static files from `clientDistDir`
    *   - `GET` catch-all → `index.html` from `clientDistDir` with `%melt.head%`
    *     replaced by `<script type="module">` tags derived from `manifest`
    *
    * `index.html` is read from `clientDistDir / "index.html"` once at startup
    * via [[fs2.io.file.Files]], making this method work on both JVM and Node.js.
    * Use `meltcIndexHtml` in `build.sbt` to have sbt-meltc copy your template
    * into `clientDistDir` automatically.
    *
    * @param app           the [[MeltKit]] router
    * @param clientDistDir the directory produced by `fastLinkJS` or `vite build`
    *                      (usually `AssetManifest.clientDistDir`)
    * @param manifest      the asset manifest used to inject script tags into
    *                      `%melt.head%` (usually `AssetManifest.manifest`)
    */
  def spaRoutes[F[_]: Async: Files](
    app:           MeltKitPlatform[F, RenderResult],
    clientDistDir: Path,
    manifest:      ViteManifest
  ): F[HttpRoutes[F]] =
    indexFallback[F](clientDistDir / "index.html", manifest.scriptTags()).map { indexR =>
      val appR   = routes(app)
      val assetR = fileService[F](FileService.Config(clientDistDir.toString))
      appR <+> Router("/assets" -> assetR) <+> indexR
    }

  // ── private helpers ────────────────────────────────────────────────────────

  /** Reads `index.html` from the filesystem once at startup and serves it
    * for every GET request that no other route handles.
    * Processes `%melt.head%` by substituting the given script tags.
    */
  private def indexFallback[F[_]: Async: Files](
    indexPath:   Path,
    headContent: String
  ): F[HttpRoutes[F]] =
    Files[F]
      .readAll(indexPath)
      .through(fs2.text.utf8.decode)
      .compile
      .string
      .map { content =>
        val html = Template.fromString(content).render(headContent)
        HttpRoutes[F] { req =>
          if req.method.name == "GET" then
            OptionT.some(
              Http4sResponse[F](
                status  = Status.Ok,
                headers = Headers(`Content-Type`(MediaType.text.html, Charset.`UTF-8`)),
                body    = fs2.Stream.emit(html).through(fs2.text.utf8.encode)
              )
            )
          else OptionT.none
        }
      }

  private[http4s] def toHttp4sResponse[F[_]](r: Response): Http4sResponse[F] =
    val status = Status.fromInt(r.status: Int).getOrElse(Status.InternalServerError)
    val ct     = MediaType.parse(r.contentType).toOption.map(`Content-Type`(_))
    val rawHeaders: List[org.http4s.Header.ToRaw] = r.headers.toList.map {
      case (k, v) =>
        org.http4s.Header.Raw(org.typelevel.ci.CIString(k), v)
    }
    val allHeaders: List[org.http4s.Header.ToRaw] = ct.toList.map(h => h: org.http4s.Header.ToRaw) ++ rawHeaders
    Http4sResponse(
      status  = status,
      headers = Headers(allHeaders*),
      body    = fs2.Stream.emit(r.body).through(fs2.text.utf8.encode)
    )
