/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.scalajs.js
import scala.util.{ Failure, Success }
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.codec.BodyDecoder
import meltkit.exceptions.BodyDecodeException

/** Bridges node:http requests to the [[MeltApp]] routing pipeline.
  *
  * Fixed to `Future` — all body reading and response writing use `Future`
  * directly, with no `asInstanceOf` casts. For `IO`-based Node.js servers,
  * use `meltkit-adapter-http4s` instead.
  */
private[meltkit] class NodeHttpBinding(
  app:    MeltApp[Future],
  config: ServerConfig
)(using ec: ExecutionContext):

  def handleRequest(req: IncomingMessage, res: ServerResponse): Unit =
    val method   = req.method.toUpperCase
    val rawUrl   = if req.url == null then "/" else req.url
    val url      = Url.parse(rawUrl, s"http://${ config.host }:${ config.port }")
    val segments = url.pathname.split('/').filter(_.nonEmpty).toList

    val hdrs    = parseHeaders(req.headers)
    val cookies = hdrs.get("cookie").map(CookieJar.parseCookieHeader).getOrElse(Map.empty)
    val nonce   = config.cspConfig.map(_ => CspNonce.generate())

    val rawBody: Future[String] = readBody(req)

    val isHead       = method == "HEAD"
    val routeMethod  = if isHead then "GET" else method
    val parsedMethod = HttpMethod.fromString(routeMethod)

    val locals = new Locals()
    nonce.foreach(n => locals.set(CspNonce.localsKey, n))

    val factory = new MeltContextFactory[Future, RenderResult]:
      def build[P <: AnyNamedTuple, B](
        params:      P,
        bodyDecoder: BodyDecoder[B]
      ): MeltContext[Future, P, B, RenderResult] =
        NodeMeltContext(
          params       = params,
          requestPath  = url.pathname,
          _queryParams = url.searchParams,
          bodyDecoder  = bodyDecoder,
          rawBody      = rawBody,
          rawHeaders   = hdrs,
          rawCookies   = cookies,
          templateOpt  = Some(config.template),
          manifest     = config.manifest,
          lang         = "en",
          basePath     = config.basePath,
          locals       = locals,
          nonce        = nonce
        )

    // Try static file serving first (GET/HEAD only)
    if (routeMethod == "GET" || isHead) && tryServeStaticFile(url.pathname, res, isHead) then return

    val matched = parsedMethod.flatMap { m =>
      app.routes.find { r =>
        r.method == m && PathSegment.matches(r.segments, segments)
      }
    }

    matched match
      case None =>
        app.notFoundHandler match
          case None =>
            res.writeHead(404, js.Dictionary("Content-Type" -> "text/plain; charset=utf-8"))
            res.end("Not Found")
          case Some(handler) =>
            val event = buildRequestEvent(url, hdrs, cookies, locals, routeMethod)
            val inner = Future(()).flatMap(_ => handler(factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])))
            val wrapped = runHooks(app.hooks, event, inner)
            writeResponse(wrapped, res, isHead)

      case Some(route) =>
        val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
        route.tryHandle(rawValues, factory) match
          case None =>
            res.writeHead(404, js.Dictionary("Content-Type" -> "text/plain; charset=utf-8"))
            res.end("Not Found")
          case Some(thunk) =>
            val event   = buildRequestEvent(url, hdrs, cookies, locals, routeMethod)
            val inner   = Future(()).flatMap(_ => thunk())
            val wrapped = runHooks(app.hooks, event, inner)
            writeResponse(wrapped, res, isHead)

  private def writeResponse(effect: Future[Response], res: ServerResponse, isHead: Boolean): Unit =
    var responded = false
    effect.onComplete {
      case Success(response) if !responded =>
        responded = true
        val headerDict = js.Dictionary[String]("Content-Type" -> response.contentType)
        response.headers.foreach { case (k, v) => headerDict(k) = v }
        response.responseCookies.foreach { c =>
          val existing   = headerDict.get("Set-Cookie").getOrElse("")
          val serialized = serializeCookie(c)
          headerDict("Set-Cookie") = if existing.isEmpty then serialized else s"$existing, $serialized"
        }
        res.writeHead(response.status, headerDict)
        if isHead then res.end()
        else res.end(response.body)

      case Failure(error) if !responded =>
        responded = true
        error match
          case bde: BodyDecodeException =>
            res.writeHead(400, js.Dictionary("Content-Type" -> "text/plain; charset=utf-8"))
            res.end(bde.error.message)
          case _ =>
            res.writeHead(500, js.Dictionary("Content-Type" -> "text/plain; charset=utf-8"))
            res.end("Internal Server Error")

      case _ => ()
    }

  private def serializeCookie(c: ResponseCookie): String =
    val sb = new StringBuilder
    sb.append(s"${ c.name }=${ c.value }")
    sb.append(s"; Path=${ c.options.path }")
    c.options.maxAge.foreach(ma => sb.append(s"; Max-Age=$ma"))
    c.options.domain.foreach(d => sb.append(s"; Domain=$d"))
    if c.options.httpOnly then sb.append("; HttpOnly")
    if c.options.secure || c.options.sameSite == "None" then sb.append("; Secure")
    sb.append(s"; SameSite=${ c.options.sameSite }")
    sb.result()

  private def readBody(req: IncomingMessage): Future[String] =
    val promise = Promise[String]()
    val chunks  = new StringBuilder
    req.on("data", (chunk: js.Any) => chunks.append(chunk.toString))
    req.on("end", (_: js.Any) => promise.success(chunks.result()))
    req.on("error", (err: js.Any) => promise.failure(new RuntimeException(err.toString)))
    promise.future

  private def parseHeaders(dict: js.Dictionary[String]): Map[String, String] =
    val builder = Map.newBuilder[String, String]
    dict.foreach { case (k, v) => builder += (k.toLowerCase -> v) }
    builder.result()

  private def tryServeStaticFile(pathname: String, res: ServerResponse, isHead: Boolean): Boolean =
    config.clientDistDir match
      case None          => false
      case Some(distDir) =>
        if pathname == "/" || pathname == "/index.html" then return false
        if pathname.contains("..") then return false

        val normalized = NodePath.normalize(pathname.stripPrefix("/"))
        if normalized.startsWith("..") || normalized.contains("..") then return false

        val filePath     = NodePath.join(distDir, normalized)
        val resolvedPath = NodePath.resolve(filePath)
        val resolvedDir  = NodePath.resolve(distDir)
        if !resolvedPath.startsWith(resolvedDir) then return false
        if !NodeFs.existsSync(filePath) then return false

        val stats = NodeFs.lstatSync(filePath)
        if !stats.isFile() || stats.isSymbolicLink() then return false

        val ext         = NodePath.extname(filePath).toLowerCase
        val contentType = mimeType(ext)
        val isHashed    = pathname.matches(".*-[a-f0-9]{8,}\\.[a-z]+$") ||
          pathname.matches(".*\\.[a-f0-9]{8,}\\.[a-z]+$")
        val cacheControl =
          if isHashed then "public, max-age=31536000, immutable"
          else "no-cache"

        NodeFs.readFile(
          filePath,
          (err, data) =>
            if err != null then
              res.writeHead(500, js.Dictionary("Content-Type" -> "text/plain"))
              res.end("Internal Server Error")
            else
              res.writeHead(
                200,
                js.Dictionary(
                  "Content-Type"  -> contentType,
                  "Cache-Control" -> cacheControl
                )
              )
              if isHead then res.end()
              else res.asInstanceOf[js.Dynamic].end(js.Dynamic.global.Buffer.from(data))
        )
        true

  private def mimeType(ext: String): String = ext match
    case ".html"          => "text/html; charset=utf-8"
    case ".css"           => "text/css; charset=utf-8"
    case ".js" | ".mjs"   => "application/javascript; charset=utf-8"
    case ".json"          => "application/json; charset=utf-8"
    case ".png"           => "image/png"
    case ".jpg" | ".jpeg" => "image/jpeg"
    case ".gif"           => "image/gif"
    case ".svg"           => "image/svg+xml"
    case ".ico"           => "image/x-icon"
    case ".woff"          => "font/woff"
    case ".woff2"         => "font/woff2"
    case ".ttf"           => "font/ttf"
    case ".map"           => "application/json"
    case ".wasm"          => "application/wasm"
    case ".txt"           => "text/plain; charset=utf-8"
    case ".xml"           => "application/xml; charset=utf-8"
    case _                => "application/octet-stream"

  private def runHooks(
    hooks: List[ServerHook[Future]],
    event: RequestEvent[Future],
    inner: Future[Response]
  ): Future[Response] =
    if hooks.isEmpty then inner
    else
      val combined = ServerHook.sequence(hooks*)
      combined.handle(
        event,
        new Resolve[Future]:
          def apply():                        Future[Response] = inner
          def apply(options: ResolveOptions): Future[Response] = inner
      )

  private def buildRequestEvent(
    meltUrl:       Url,
    hdrs:          Map[String, String],
    parsedCookies: Map[String, String],
    sharedLocals:  Locals,
    httpMethod:    String
  ): RequestEvent[Future] =
    new RequestEvent[Future]:
      val method      = httpMethod
      val requestPath = meltUrl.pathname
      def query(name:    String): Option[String] = meltUrl.query(name)
      def queryAll(name: String): List[String]   = meltUrl.queryAll(name)
      val queryParams = meltUrl.searchParams
      def header(name:   String): Option[String] = hdrs.get(name.toLowerCase)
      val headers = hdrs
      def cookie(name:   String): Option[String] = parsedCookies.get(name)
      val cookies = parsedCookies
      val locals = sharedLocals
      val cookieJar = CookieJar(parsedCookies)
      val url = meltUrl
      val routeId = None
      val isDataRequest = false
