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
import meltkit.exceptions.BodyDecodeException
import org.http4s.headers.`Content-Type`
import org.http4s.headers.`Set-Cookie` as Http4sSetCookie
import org.http4s.headers.Cookie as Http4sCookieHeader
import org.http4s.server.staticcontent.fileService
import org.http4s.server.staticcontent.FileService
import org.http4s.Charset
import org.http4s.Headers
import org.http4s.HttpRoutes
import org.http4s.MediaType
import org.http4s.Response as Http4sResponse
import org.http4s.ResponseCookie as Http4sResponseCookie
import org.http4s.SameSite
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
final class Http4sAdapter[F[_]: Concurrent: meltkit.Defer] private (
  private val app:       ServerMeltKitPlatform[F],
  private val template:  Template,
  private val manifest:  ViteManifest,
  private val lang:      String,
  private val basePath:  String,
  private val cspConfig: Option[CspConfig] = None
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

      val locals = new Locals()

      // Generate a per-request nonce when CSP is configured and store it in locals.
      val nonce = cspConfig.map(_ => CspNonce.generate())
      nonce.foreach(n => locals.set(CspNonce.localsKey, n))

      val factory = new MeltContextFactory[F, RenderResult]:
        def build[P <: AnyNamedTuple, B](
          params:      P,
          bodyDecoder: BodyDecoder[B]
        ): MeltContext[F, P, B, RenderResult] =
          Http4sMeltContext(params, request, bodyDecoder, Some(template), manifest, lang, basePath, locals, nonce)

      // Attach the Content-Security-Policy header to the response after the handler completes.
      def withCspHeader(effect: F[Response]): F[Response] =
        cspConfig.zip(nonce).fold(effect) {
          case (cfg, n) =>
            effect.map { response =>
              val headerName = if cfg.reportOnly then "Content-Security-Policy-Report-Only"
              else "Content-Security-Policy"
              val headerValue = Http4sAdapter.buildCspValue(cfg.directives, n)
              response.withHeaders(response.headers + (headerName -> headerValue))
            }
        }

      val event = Http4sAdapter.buildRequestEvent(request, locals)

      matched match
        case None =>
          app.notFoundHandler match
            case None          => OptionT.none
            case Some(handler) =>
              val innerEffect = meltkit.Defer[F].defer {
                handler(factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]]))
              }
              val wrapped = Http4sAdapter.runHooks(app.hooks, event, innerEffect)
              OptionT.liftF(withCspHeader(wrapped).map(Http4sAdapter.toHttp4sResponse[F]))
        case Some(route) =>
          val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
          route.tryHandle(rawValues, factory) match
            case None        => OptionT.none
            case Some(thunk) =>
              val innerEffect = meltkit.Defer[F].defer {
                thunk().handleErrorWith {
                  case e: BodyDecodeException =>
                    Concurrent[F].pure(Response.badRequest(e.error.message))
                  case e: Throwable =>
                    app.errorHandler match
                      case None             => Concurrent[F].raiseError(e)
                      case Some(errHandler) =>
                        val errorCtx = factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])
                        errHandler(errorCtx, e).handleErrorWith { _ =>
                          Concurrent[F].pure(PlainResponse(500, "text/plain; charset=utf-8", "Internal Server Error"))
                        }
                }
              }
              val wrapped = Http4sAdapter.runHooks(app.hooks, event, innerEffect)
              OptionT.liftF(withCspHeader(wrapped).map(Http4sAdapter.toHttp4sResponse[F]))
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

  /** Bridges cats [[cats.Applicative]] to [[meltkit.Pure]].
    *
    * Any `F` that has a cats `Applicative` instance (e.g. `cats.effect.IO`) gets
    * a `Pure[F]` automatically when `Http4sAdapter.given` is imported.
    */
  given [F[_]: cats.Applicative]: meltkit.Pure[F] with
    override def pure[A](a: A): F[A] = cats.Applicative[F].pure(a)

  /** Bridges cats-effect [[cats.effect.kernel.Sync]] to [[meltkit.Defer]].
    *
    * Any `F` that has a cats-effect `Sync` instance (e.g. `cats.effect.IO`) gets
    * a `Defer[F]` automatically when `Http4sAdapter.given` is imported.
    */
  given [F[_]](using S: cats.effect.kernel.Sync[F]): meltkit.Defer[F] with
    def defer[A](fa: => F[A]): F[A] = S.defer(fa)

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
    * @param basePath      the app's deployment root path (e.g. `""` for root, `"/myapp"` for
    *                      sub-path). Note: Vite manifest `entry.file` already contains the
    *                      `assets/` prefix, so this should be `""` for root deployments.
    */
  def apply[F[_]: Async: Files: meltkit.Defer](
    app:           ServerMeltKitPlatform[F],
    clientDistDir: Path,
    manifest:      ViteManifest,
    lang:          String = "en",
    basePath:      String = "",
    cspConfig:     Option[CspConfig] = None
  ): F[Http4sAdapter[F]] =
    Files[F]
      .readAll(clientDistDir / "index.html")
      .through(fs2.text.utf8.decode)
      .compile
      .string
      .map(content => new Http4sAdapter(app, Template.fromString(content), manifest, lang, basePath, cspConfig))

  /** Builds [[HttpRoutes]] for a full SSR setup:
    *
    *   - MeltKit routes (SSR page rendering + API endpoints)
    *   - All paths → static files from `clientDistDir` (Vite output served at root),
    *     excluding `"/"` and `"/index.html"` which are handled by SSR route handlers
    *
    * This is the SSR counterpart of [[spaRoutes]]. It combines the filtered static
    * file service with the adapter's SSR-capable routes so that callers do not need
    * to wire up the file service manually.
    *
    * `index.html` is read from `clientDistDir / "index.html"` once at startup.
    * Use `meltcIndexHtml` in `build.sbt` to have sbt-meltc copy the template
    * into `clientDistDir` automatically.
    *
    * @param app           the [[MeltKit]] router
    * @param clientDistDir the directory produced by `vite build`
    *                      (usually `AssetManifest.clientDistDir`)
    * @param manifest      the asset manifest (usually `AssetManifest.manifest`)
    * @param lang          default HTML `lang` attribute (default `"en"`)
    * @param basePath      the app's deployment root path (default `""`)
    * @param cspConfig     optional CSP configuration
    */
  def ssrRoutes[F[_]: Async: Files: meltkit.Defer](
    app:           ServerMeltKitPlatform[F],
    clientDistDir: Path,
    manifest:      ViteManifest,
    lang:          String = "en",
    basePath:      String = "",
    cspConfig:     Option[CspConfig] = None
  ): F[HttpRoutes[F]] =
    apply(app, clientDistDir, manifest, lang, basePath, cspConfig).map { adapter =>
      val rawFileR = fileService[F](FileService.Config(clientDistDir.toString))
      // Exclude "/" and "/index.html": those paths are handled by SSR route handlers.
      // fileService auto-serves index.html for directory requests, which would bypass
      // the SSR template rendering.
      val assetR: HttpRoutes[F] = HttpRoutes[F] { req =>
        val p = req.pathInfo.renderString
        if p == "/" || p == "/index.html" then OptionT.none
        else rawFileR(req)
      }
      assetR <+> adapter.routes
    }

  /** Builds the `Content-Security-Policy` header value.
    *
    * Appends `'nonce-{value}'` to `script-src` and `style-src` directives when present.
    * Other directives (including `default-src`) are left unchanged.
    */
  private[http4s] def buildCspValue(directives: Map[String, List[String]], nonce: String): String =
    val nonceToken   = s"'nonce-$nonce'"
    val nonceTargets = Set("script-src", "style-src")
    directives
      .map {
        case (directive, values) =>
          val finalValues =
            if nonceTargets.contains(directive) then values :+ nonceToken
            else values
          s"$directive ${ finalValues.mkString(" ") }"
      }
      .mkString("; ")

  /** Builds [[HttpRoutes]] from a [[MeltKit]] router (API routes only).
    *
    * `ctx.render()` is **not** available in route handlers registered via this
    * method. For SSR rendering use `Http4sAdapter(app, template, manifest).routes`.
    * For a complete SPA setup use [[spaRoutes]].
    */
  def routes[F[_]: Concurrent: meltkit.Defer](app: ServerMeltKitPlatform[F]): HttpRoutes[F] =
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
          val locals    = new Locals()
          val factory   = new MeltContextFactory[F, RenderResult]:
            def build[P <: AnyNamedTuple, B](
              params:      P,
              bodyDecoder: BodyDecoder[B]
            ): MeltContext[F, P, B, RenderResult] =
              Http4sMeltContext(params, request, bodyDecoder, locals = locals)
          route.tryHandle(rawValues, factory) match
            case None        => OptionT.none
            case Some(thunk) =>
              val event      = buildRequestEvent(request, locals)
              val lazyEffect = meltkit.Defer[F].defer {
                thunk().handleErrorWith {
                  case e: BodyDecodeException =>
                    Concurrent[F].pure(Response.badRequest(e.error.message))
                }
              }
              val wrapped = runHooks(app.hooks, event, lazyEffect)
              OptionT.liftF(wrapped.map(toHttp4sResponse[F]))
    }

  /** Builds [[HttpRoutes]] for a full SPA setup:
    *
    *   - MeltKit routes (API endpoints)
    *   - All paths → static files from `clientDistDir` (Vite output served at root)
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
  def spaRoutes[F[_]: Async: Files: meltkit.Defer](
    app:           ServerMeltKitPlatform[F],
    clientDistDir: Path,
    manifest:      ViteManifest
  ): F[HttpRoutes[F]] =
    // headTags with basePath="" because Vite's manifest `file` field already
    // includes the "assets/" prefix (e.g. "assets/layout-XXX.js"), so the
    // generated URL becomes "/assets/layout-XXX.js" — matching what fileService
    // resolves when serving clientDistDir at "/".
    indexFallback[F](clientDistDir / "index.html", manifest.headTags(basePath = "")).map { indexR =>
      val appR      = routes(app)
      val rawAssetR = fileService[F](FileService.Config(clientDistDir.toString))
      // Prevent fileService from serving index.html for "/" or "/index.html" —
      // those paths must be handled by indexR so that %melt.head% is processed.
      // fileService auto-serves index.html for directory requests, which would
      // bypass the template rendering logic.
      val assetR: HttpRoutes[F] = HttpRoutes[F] { req =>
        val p = req.pathInfo.renderString
        if p == "/" || p == "/index.html" then OptionT.none
        else rawAssetR(req)
      }
      appR <+> assetR <+> indexR
    }

  // ── private helpers ────────────────────────────────────────────────────────

  /** Runs a list of hooks around an inner effect, producing the final response. */
  private[http4s] def runHooks[F2[_]](
    hooks:  List[ServerHook[F2]],
    event:  RequestEvent[F2],
    inner:  F2[Response]
  ): F2[Response] =
    if hooks.isEmpty then inner
    else
      val combined = ServerHook.sequence(hooks*)
      combined.handle(event, new Resolve[F2]:
        def apply(): F2[Response]                    = inner
        def apply(options: ResolveOptions): F2[Response] = inner // ResolveOptions handled in Phase 2
      )

  private[http4s] def buildRequestEvent[F2[_]](request: org.http4s.Request[F2], sharedLocals: Locals): RequestEvent[F2] =
    new RequestEvent[F2]:
      val method      = request.method.name
      val requestPath = request.uri.path.renderString
      val locals      = sharedLocals

      def query(name: String): Option[String] =
        request.uri.query.params.get(name)

      def queryAll(name: String): List[String] =
        request.uri.query.multiParams.getOrElse(name, Nil).toList

      val queryParams: Map[String, List[String]] =
        request.uri.query.multiParams.map { case (k, v) => k -> v.toList }

      private lazy val parsedCookies: Map[String, String] =
        request.headers.get[Http4sCookieHeader] match
          case None         => Map.empty
          case Some(cookie) => cookie.values.toList.map(c => c.name -> c.content).toMap

      def cookie(name: String): Option[String] = parsedCookies.get(name)
      val cookies: Map[String, String]         = parsedCookies

      private lazy val parsedHeaders: Map[String, String] =
        request.headers.headers
          .groupBy(_.name.toString.toLowerCase)
          .map { case (name, vals) => name -> vals.map(_.value).mkString(", ") }

      def header(name: String): Option[String] = parsedHeaders.get(name.toLowerCase)
      val headers: Map[String, String]         = parsedHeaders

      val cookieJar    = CookieJar(parsedCookies)
      val url          = Url(requestPath, queryParams, "")
      val routeId      = None
      val isDataRequest = false

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

  // Converts a meltkit.ResponseCookie (framework-independent) to an
  // org.http4s.ResponseCookie so that http4s handles RFC-compliant serialization,
  // including the automatic "; Secure" for SameSite=None (RFC 6265bis).
  private[http4s] def toHttp4sResponseCookie(c: meltkit.ResponseCookie): Http4sResponseCookie =
    val sameSite = c.options.sameSite match
      case "Strict" => Some(SameSite.Strict)
      case "Lax"    => Some(SameSite.Lax)
      case "None"   => Some(SameSite.None)
    Http4sResponseCookie(
      name     = c.name,
      content  = c.value,
      maxAge   = c.options.maxAge,
      domain   = c.options.domain,
      path     = Some(c.options.path),
      sameSite = sameSite,
      secure   = c.options.secure,
      httpOnly = c.options.httpOnly
    )

  private[http4s] def toHttp4sResponse[F[_]](r: Response): Http4sResponse[F] =
    val status = Status.fromInt(r.status: Int).getOrElse(Status.InternalServerError)
    val ct     = MediaType.parse(r.contentType).toOption.map(`Content-Type`(_))
    val rawHeaders: List[org.http4s.Header.ToRaw] = r.headers.toList.map {
      case (k, v) =>
        org.http4s.Header.Raw(org.typelevel.ci.CIString(k), v)
    }
    val cookieHeaders: List[org.http4s.Header.ToRaw] = r.responseCookies.map { c =>
      Http4sSetCookie(toHttp4sResponseCookie(c))
    }
    val allHeaders: List[org.http4s.Header.ToRaw] =
      ct.toList.map(h => h: org.http4s.Header.ToRaw) ++ rawHeaders ++ cookieHeaders
    Http4sResponse(
      status  = status,
      headers = Headers(allHeaders*),
      body    = fs2.Stream.emit(r.body).through(fs2.text.utf8.encode)
    )
