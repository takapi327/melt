/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import java.io.{ InputStream, OutputStream }
import java.nio.file.{ Files, Path, Paths }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import com.sun.net.httpserver.HttpExchange
import meltkit.codec.BodyDecoder
import meltkit.exceptions.BodyDecodeException

/** Bridges JDK `HttpExchange` to the [[MeltApp]] routing pipeline.
  *
  * Fixed to `Future`. For `IO`-based JVM servers, use `meltkit-adapter-http4s`.
  */
private[meltkit] class JdkHttpBinding(
  app:    MeltApp[Future],
  config: ServerConfig
)(using ec: ExecutionContext):

  def handleExchange(exchange: HttpExchange): Unit =
    try
      val method   = exchange.getRequestMethod.toUpperCase
      val uri      = exchange.getRequestURI
      val rawUrl   = if uri.getRawPath == null then "/" else uri.getRawPath + Option(uri.getRawQuery).fold("")("?" + _)
      val url      = Url.parse(rawUrl, s"http://${ config.host }:${ config.port }")
      val segments = url.pathname.split('/').filter(_.nonEmpty).toList

      val hdrs    = parseHeaders(exchange)
      val cookies = hdrs.get("cookie").map(CookieJar.parseCookieHeader).getOrElse(Map.empty)
      val nonce   = config.cspConfig.map(_ => CspNonce.generate())

      val rawBody: Future[String] = Future(readBody(exchange.getRequestBody))

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
          JvmMeltContext(
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

      // Static file serving (GET/HEAD only)
      if (routeMethod == "GET" || isHead) && tryServeStaticFile(url.pathname, exchange, isHead) then return

      val matched = parsedMethod.flatMap { m =>
        app.routes.find { r =>
          r.method == m && PathSegment.matches(r.segments, segments)
        }
      }

      matched match
        case None =>
          app.notFoundHandler match
            case None =>
              sendText(exchange, 404, "Not Found")
            case Some(handler) =>
              val event = buildRequestEvent(url, hdrs, cookies, locals, routeMethod)
              val inner =
                Future(()).flatMap(_ => handler(factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])))
              val wrapped = runHooks(app.hooks, event, inner)
              writeResponse(wrapped, exchange, isHead)

        case Some(route) =>
          val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
          route.tryHandle(rawValues, factory) match
            case None =>
              sendText(exchange, 404, "Not Found")
            case Some(thunk) =>
              val event   = buildRequestEvent(url, hdrs, cookies, locals, routeMethod)
              val inner   = Future(()).flatMap(_ => thunk())
              val wrapped = runHooks(app.hooks, event, inner)
              writeResponse(wrapped, exchange, isHead)
    catch
      case e: Throwable =>
        try sendText(exchange, 500, "Internal Server Error")
        catch case _: Throwable => ()

  private def writeResponse(effect: Future[Response], exchange: HttpExchange, isHead: Boolean): Unit =
    effect.onComplete {
      case Success(response) =>
        try
          response.headers.foreach {
            case (k, v) =>
              exchange.getResponseHeaders.set(k, v)
          }
          exchange.getResponseHeaders.set("Content-Type", response.contentType)
          response.responseCookies.foreach { c =>
            exchange.getResponseHeaders.add("Set-Cookie", serializeCookie(c))
          }
          val bodyBytes = response.body.getBytes("UTF-8")
          if isHead then exchange.sendResponseHeaders(response.status, -1)
          else
            exchange.sendResponseHeaders(response.status, bodyBytes.length.toLong)
            val os = exchange.getResponseBody
            os.write(bodyBytes)
            os.close()
          exchange.close()
        catch
          case _: Throwable =>
            try exchange.close()
            catch case _: Throwable => ()

      case Failure(error) =>
        try
          error match
            case bde: BodyDecodeException =>
              sendText(exchange, 400, bde.error.message)
            case _ =>
              sendText(exchange, 500, "Internal Server Error")
        catch
          case _: Throwable =>
            try exchange.close()
            catch case _: Throwable => ()
    }

  private def sendText(exchange: HttpExchange, status: Int, body: String): Unit =
    val bytes = body.getBytes("UTF-8")
    exchange.getResponseHeaders.set("Content-Type", "text/plain; charset=utf-8")
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val os = exchange.getResponseBody
    os.write(bytes)
    os.close()
    exchange.close()

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

  private def readBody(is: InputStream): String =
    try new String(is.readAllBytes(), "UTF-8")
    finally is.close()

  private def parseHeaders(exchange: HttpExchange): Map[String, String] =
    val builder = Map.newBuilder[String, String]
    exchange.getRequestHeaders.forEach { (k, vs) =>
      builder += (k.toLowerCase -> vs.toArray.mkString(", "))
    }
    builder.result()

  private def tryServeStaticFile(pathname: String, exchange: HttpExchange, isHead: Boolean): Boolean =
    config.clientDistDir match
      case None          => false
      case Some(distDir) =>
        if pathname == "/" || pathname == "/index.html" then return false
        if pathname.contains("..") then return false

        val normalized = pathname.stripPrefix("/")
        val filePath   = Paths.get(distDir, normalized).normalize()
        val distPath   = Paths.get(distDir).normalize()

        if !filePath.startsWith(distPath) then return false
        if !Files.exists(filePath) || !Files.isRegularFile(filePath) then return false
        if Files.isSymbolicLink(filePath) then return false

        val ext = Option(filePath.getFileName.toString)
          .flatMap(n => Option(n.lastIndexOf('.')).filter(_ >= 0).map(i => n.substring(i)))
          .getOrElse("")
        val contentType = mimeType(ext.toLowerCase)
        val isHashed    = pathname.matches(".*-[a-f0-9]{8,}\\.[a-z]+$") ||
          pathname.matches(".*\\.[a-f0-9]{8,}\\.[a-z]+$")
        val cacheControl =
          if isHashed then "public, max-age=31536000, immutable"
          else "no-cache"

        val bytes = Files.readAllBytes(filePath)
        exchange.getResponseHeaders.set("Content-Type", contentType)
        exchange.getResponseHeaders.set("Cache-Control", cacheControl)
        if isHead then exchange.sendResponseHeaders(200, -1)
        else
          exchange.sendResponseHeaders(200, bytes.length.toLong)
          val os = exchange.getResponseBody
          os.write(bytes)
          os.close()
        exchange.close()
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
