/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import scala.NamedTuple.AnyNamedTuple

import java.io.File

import zio.ZIO
import zio.http.codec.PathCodec.trailing
import zio.http.{ handler, Handler, Method, Path, Request, Response as ZResponse, Routes, Status }

import melt.runtime.render.RenderResult

import meltkit.*
import meltkit.codec.BodyDecoder
import meltkit.exceptions.BodyDecodeException

import ZioInstances.ZTask

/** Serves a [[meltkit.MeltKit]] router over zio-http.
  *
  * The environment `R` is kept: `MeltKit` is instantiated at `[A] =>> ZIO[R, Throwable, A]`, so
  * handlers can use `ZIO.service` / `ZLayer` and the resulting `Routes[R, Response]` carries the
  * same requirement through to `Server.serve`.
  *
  * {{{
  * type Env    = PostStore
  * type App[A] = ZIO[Env, Throwable, A]
  *
  * import meltkit.adapter.ziohttp.ZioInstances.given
  *
  * val app = MeltKit[App]()
  * app.get("posts") { ctx => ZIO.serviceWithZIO[PostStore](_.list).map(ctx.ok(_)) }
  *
  * Server.serve(ZioHttpAdapter.routes(app)).provide(Server.default, postStoreLayer)
  * }}}
  */
object ZioHttpAdapter:

  /** Builds `Routes` from a [[meltkit.MeltKit]] router (API routes only).
    *
    * Melt matches routes itself, so everything arrives through a single `Method.ANY / trailing`
    * catch-all rather than being translated into zio-http `RoutePattern`s. `Method.ANY` reaches
    * GET / POST / PUT / DELETE / PATCH — the methods `HttpMethod` accepts — plus OPTIONS, which
    * is what lets CORS preflight be answered here.
    *
    * `ctx.render()` is not available on routes registered through this method; that needs the
    * SSR entry point (a later phase).
    */
  def routes[R](app: ServerMeltKitPlatform[ZTask[R]]): Routes[R, ZResponse] =
    build(app, Ssr.none)

  /** Builds `Routes` for a full SSR setup: page rendering, API endpoints, and the client's
    * static assets.
    *
    * Static files are served from inside the catch-all rather than by composing
    * `Middleware.serveDirectory` with `++`. That middleware terminates every GET — returning 404
    * when no file matches instead of falling through — so composing it either way would make
    * every SSR page 404. Here a request reaches the file system only after Melt's router has
    * declined it, which keeps the SSR pages reachable and matches how the http4s adapter
    * excludes `/` and `/index.html` from its file service.
    *
    * @param app           the [[meltkit.MeltKit]] router
    * @param clientDistDir the client build output (`AssetManifest.clientDistDir`)
    * @param manifest      the asset manifest (`AssetManifest.manifest`)
    * @param lang          default HTML `lang` attribute
    * @param basePath      the app's deployment root path
    */
  def ssrRoutes[R](
    app:             ServerMeltKitPlatform[ZTask[R]],
    clientDistDir:   File,
    manifest:        ViteManifest,
    lang:            String = "en",
    basePath:        String = "",
    cspConfig:       Option[CspConfig] = None,
    routerHydration: Option[String] = None
  ): ZIO[Any, Throwable, Routes[R, ZResponse]] =
    readIndexHtml(clientDistDir).map { template =>
      build(
        app,
        Ssr(Some(template), manifest, lang, basePath, Some(clientDistDir), cspConfig, routerHydration)
      )
    }

  /** Reads the SSR shell once at startup, mirroring the http4s adapter. */
  private def readIndexHtml(clientDistDir: File): ZIO[Any, Throwable, Template] =
    ZIO.attemptBlocking {
      val path = new File(clientDistDir, "index.html").toPath
      Template.fromString(java.nio.file.Files.readString(path))
    }

  /** The SSR-specific inputs a request needs; absent for the API-only entry point. */
  private final case class Ssr(
    template:      Option[Template],
    manifest:      ViteManifest,
    lang:          String,
    basePath:      String,
    clientDistDir: Option[File],
    cspConfig:     Option[CspConfig] = None,
    routerEntry:   Option[String] = None
  )

  private object Ssr:
    val none: Ssr = Ssr(None, ViteManifest.empty, "en", "", None)

  private def build[R](app: ServerMeltKitPlatform[ZTask[R]], ssr: Ssr): Routes[R, ZResponse] =
    Routes(
      Method.ANY / trailing -> handler { (path: Path, request: Request) =>
        dispatch(app, path, request, ssr)
      }
    )

  /** Serves `path` from the client build when it names a real file inside it.
    *
    * `Handler.fromFile` needs a `Scope`, hence `ZIO.scoped`. The canonical-path check keeps a
    * crafted `..` from escaping the directory.
    */
  private def staticFile(dir: File, path: Path): ZIO[Any, Nothing, Option[ZResponse]] =
    val rel = path.encode.stripPrefix("/")
    if rel.isEmpty then ZIO.none
    else
      ZIO
        .attempt {
          val f = new File(dir, rel)
          Option.when(f.isFile && f.getCanonicalPath.startsWith(dir.getCanonicalPath))(f)
        }
        .flatMap {
          case None    => ZIO.none
          case Some(f) => ZIO.scoped(Handler.fromFile(f).apply(Request.get(path.encode))).map(Some(_))
        }
        .orElseSucceed(None)

  /** Runs one request through Melt's router.
    *
    * The error channel is discharged here, so the result is `Routes[R, Response]` and
    * `Server.serve` accepts it without a `sandbox` at the call site (design §4). `catchAllCause`
    * rather than `catchAll`: Melt's render paths throw synchronously, which surfaces as a defect
    * that `catchAll` would let escape.
    */
  private def dispatch[R](
    app:     ServerMeltKitPlatform[ZTask[R]],
    path:    Path,
    request: Request,
    ssr:     Ssr
  ): ZIO[R, Nothing, ZResponse] =
    val segments = path.segments.toList
    val locals   = new Locals()
    val corsCfg  = app.corsConfig

    // A per-request CSP nonce, stored in locals so the rendered page can reference it.
    val nonce = ssr.cspConfig.map(_ => CspNonce.generate())
    nonce.foreach(n => locals.set(CspNonce.localsKey, n))

    val factory = new MeltContextFactory[ZTask[R], RenderResult]:
      def build[P <: AnyNamedTuple, B](
        params:      P,
        bodyDecoder: BodyDecoder[B]
      ): MeltContext[ZTask[R], P, B, RenderResult] =
        ZioHttpMeltContext(
          params,
          request,
          bodyDecoder,
          locals,
          ssr.template,
          ssr.manifest,
          ssr.lang,
          ssr.basePath,
          nonce,
          Some(app),
          ssr.routerEntry
        )

    val corsPreflight: Option[ZResponse] = corsCfg.collect {
      case cfg if Cors.isPreflight(RequestAdapters.corsView(request)) =>
        ResponseConversion.toZioResponse(
          PlainResponse(204, "text/plain; charset=utf-8", "", Cors.preflightHeaders(cfg, RequestAdapters.corsView(request)))
        )
    }

    val matched = HttpMethod.fromString(request.method.name).flatMap { m =>
      app.routes.find(r => r.method == m && PathSegment.matches(r.segments, segments))
    }

    val effect: ZIO[R, Throwable, Response] = matched match
      case None =>
        app.notFoundHandler match
          case None          => ZIO.succeed(Response.notFound("Not Found"))
          case Some(handler) =>
            ZIO.suspendSucceed(handler(factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])))
      case Some(route) =>
        val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
        route.tryHandle(rawValues, factory) match
          case None        => ZIO.succeed(Response.notFound("Not Found"))
          case Some(thunk) => ZIO.suspendSucceed(thunk()).catchAll(recover(app, factory, _))

    /** Attaches the `Content-Security-Policy` header once the handler has produced a response. */
    def withCsp(e: ZIO[R, Throwable, Response]): ZIO[R, Throwable, Response] =
      ssr.cspConfig.zip(nonce).fold(e) { (cfg, n) =>
        e.map(resp => resp.withHeaders(resp.headers + (cfg.headerName -> cfg.buildHeaderValue(n))))
      }

    /** Attaches the actual-request CORS headers, merging `Vary` with whatever the handler set. */
    def withCors(e: ZIO[R, Throwable, Response]): ZIO[R, Throwable, Response] =
      corsCfg.fold(e) { cfg =>
        val cors = Cors.actualHeaders(cfg, RequestAdapters.corsView(request))
        if cors.isEmpty then e
        else
          e.map { resp =>
            val merged = cors.get("Vary").fold(cors) { v =>
              cors + ("Vary" -> Cors.mergeVary(resp.headers.get("Vary"), v))
            }
            resp.addHeaders(merged)
          }
      }

    val event   = RequestAdapters.requestEvent[R](request, locals)
    val hooked  = RequestAdapters.runHooks(app.hooks, event, effect)

    val rendered = withCsp(withCors(hooked))
      .map(ResponseConversion.toZioResponse)
      .catchAllCause(cause => ZIO.succeed(internalServerError(cause.squash)))

    // Melt declined the request: try the client build before settling for the 404.
    val routeOrStatic = ssr.clientDistDir match
      case Some(dir) if matched.isEmpty && app.notFoundHandler.isEmpty =>
        staticFile(dir, path).flatMap {
          case Some(file) => ZIO.succeed(file)
          case None       => rendered
        }
      case _ => rendered

    // A CORS preflight is answered before routing: OPTIONS is not a routable Melt method.
    corsPreflight match
      case Some(res) => ZIO.succeed(res)
      case None      => routeOrStatic

  /** Mirrors the http4s adapter's handler-level recovery: a body-decode failure becomes a 400,
    * and anything else goes to the app's error handler when one is registered.
    *
    * With no error handler the failure is re-raised and [[dispatch]]'s `catchAllCause` turns it
    * into a 500 — the same outcome http4s reaches by letting the server catch it (design §4.2).
    */
  private def recover[R](
    app:     ServerMeltKitPlatform[ZTask[R]],
    factory: MeltContextFactory[ZTask[R], RenderResult],
    error:   Throwable
  ): ZIO[R, Throwable, Response] =
    error match
      case e: BodyDecodeException => ZIO.succeed(Response.badRequest(e.error.message))
      case e                      =>
        app.errorHandler match
          case None => ZIO.fail(e)
          case Some(errHandler) =>
            val errorCtx = factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])
            ZIO
              .suspendSucceed(errHandler(errorCtx, e))
              .catchAllCause(_ => ZIO.succeed(PlainResponse(500, "text/plain; charset=utf-8", "Internal Server Error")))

  private def internalServerError(e: Throwable): ZResponse =
    ZResponse.text(Option(e.getMessage).getOrElse("Internal Server Error")).status(Status.InternalServerError)
