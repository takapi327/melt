/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import meltkit.*
import zio.http.Request
import zio.ZIO
import ZioInstances.ZTask

/** Views a zio-http `Request` through the transport-neutral interfaces `meltkit` hooks and CORS
  * work against.
  */
private[ziohttp] object RequestAdapters:

  /** Exposes the request to [[meltkit.ServerHook]]s. */
  def requestEvent[R](request: Request, sharedLocals: Locals): RequestEvent[ZTask[R]] =
    new RequestEvent[ZTask[R]]:
      val method       = request.method.name
      val requestPath  = request.path.encode
      val pathSegments = request.path.segments.toList
      val locals       = sharedLocals

      def query(name: String): Option[String] = request.queryParam(name)

      def queryAll(name: String): List[String] = request.queryParameters.getAll(name).toList

      val queryParams: Map[String, List[String]] =
        request.queryParameters.map.map((k, v) => k -> v.toList).toMap

      private lazy val parsedCookies: Map[String, String] =
        request.cookies.map(c => c.name -> c.content).toMap

      def cookie(name: String): Option[String]      = parsedCookies.get(name)
      val cookies:              Map[String, String] = parsedCookies

      private lazy val parsedHeaders: Map[String, String] =
        request.headers.map(h => h.headerName.toLowerCase -> h.renderedValue).toMap

      def header(name: String): Option[String]      = parsedHeaders.get(name.toLowerCase)
      val headers:              Map[String, String] = parsedHeaders

      val cookieJar     = CookieJar(parsedCookies)
      val url           = Url(requestPath, queryParams, "")
      val routeId       = None
      val isDataRequest = false

  /** Exposes the method and headers [[meltkit.Cors]] needs to decide on a request. */
  def corsView(request: Request): CorsRequestView =
    new CorsRequestView:
      def method:               String         = request.method.name
      def header(name: String): Option[String] = request.headers.get(name)

  /** Runs the app's hooks around `inner`.
    *
    * `ResolveOptions` is not honoured yet, matching the http4s adapter.
    */
  def runHooks[R](
    hooks: List[ServerHook[ZTask[R]]],
    event: RequestEvent[ZTask[R]],
    inner: ZIO[R, Throwable, Response]
  ): ZIO[R, Throwable, Response] =
    if hooks.isEmpty then inner
    else
      ServerHook
        .sequence(hooks*)
        .handle(
          event,
          new Resolve[ZTask[R]]:
            def apply():                        ZIO[R, Throwable, Response] = inner
            def apply(options: ResolveOptions): ZIO[R, Throwable, Response] = inner
        )
