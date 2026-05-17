/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }

/** User-facing builder API for starting a JDK HTTP server.
  *
  * Uses `com.sun.net.httpserver.HttpServer` — no external dependencies.
  * Fixed to `Future`. For `IO`-based servers, use `meltkit-adapter-http4s`.
  *
  * {{{
  * JdkServer
  *   .builder(app)
  *   .withPort(9092)
  *   .withTemplate(template)
  *   .withManifest(manifest)
  *   .withClientDistDir(distDir)
  *   .start()
  *   .foreach { server =>
  *     println(s"Running on port ${server.port}")
  *   }
  * }}}
  */
object JdkServer:

  def builder(app: MeltApp[Future])(using ExecutionContext): Builder =
    Builder(app)

  case class Builder(
    app:              MeltApp[Future],
    host:             String               = "0.0.0.0",
    port:             Int                  = 3000,
    templateContent:  Option[String]       = None,
    manifestJson:     Option[String]       = None,
    manifestInstance: Option[ViteManifest] = None,
    basePath:         String               = "",
    clientDistDir:    Option[String]       = None
  )(using ec: ExecutionContext):
    def withHost(h:            String):       Builder = copy(host = h)
    def withPort(p:            Int):          Builder = copy(port = p)
    def withTemplate(content:  String):       Builder = copy(templateContent = Some(content))
    def withManifest(json:     String):       Builder = copy(manifestJson = Some(json))
    def withManifest(m:        ViteManifest): Builder = copy(manifestInstance = Some(m))
    def withBasePath(bp:       String):       Builder = copy(basePath = bp)
    def withClientDistDir(dir: String):       Builder = copy(clientDistDir = Some(dir))

    def start(): Future[RunningServer[Future]] =
      val manifest = manifestInstance
        .orElse(manifestJson.map(j => ViteManifest.fromString(j)))
        .getOrElse(ViteManifest.empty)
      val config = ServerConfig(
        host          = host,
        port          = port,
        template      = Template.fromString(templateContent.getOrElse("")),
        manifest      = manifest,
        basePath      = basePath,
        cspConfig     = app.cspConfig,
        clientDistDir = clientDistDir
      )
      JdkServerAdapter().start(app, config)
