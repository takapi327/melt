/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }

/** User-facing builder API for starting a Node.js HTTP server.
  *
  * Fixed to `Future` — no cats-effect or http4s dependency required.
  * For `IO`-based servers, use `meltkit-adapter-http4s` instead.
  *
  * {{{
  * NodeServer
  *   .builder(app)
  *   .withPort(3000)
  *   .withTemplate(loadTemplate())
  *   .withClientDistDir(AssetManifest.clientDistDir)
  *   .withManifest(AssetManifest.manifest)
  *   .start()
  *   .foreach { server =>
  *     println(s"Running on port ${server.port}")
  *   }
  * }}}
  */
object NodeServer:

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
    def withHost(h:           String):       Builder = copy(host = h)
    def withPort(p:           Int):          Builder = copy(port = p)
    def withTemplate(content: String):       Builder = copy(templateContent = Some(content))
    def withManifest(json:    String):       Builder = copy(manifestJson = Some(json))
    def withManifest(m:       ViteManifest): Builder = copy(manifestInstance = Some(m))
    def withBasePath(bp:      String):       Builder = copy(basePath = bp)

    /** Sets the directory containing client build output (JS/CSS assets).
      * Enables static file serving for hydration scripts and stylesheets.
      */
    def withClientDistDir(dir: String): Builder = copy(clientDistDir = Some(dir))

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
      NodeServerAdapter().start(app, config)
