/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A server adapter that starts an HTTP server for a [[MeltApp]].
  *
  * The core module does not depend on cats-effect, so the lifecycle uses
  * a simple callback pattern instead of `Resource`.
  *
  * Platform-specific modules provide concrete implementations:
  *   - `meltkit-node`:        [[NodeServerAdapter]] (node:http direct)
  *   - `meltkit` (jvm, future): `JdkServerAdapter` (JDK HttpServer)
  */
trait ServerAdapter[F[_]]:

  /** Starts the server and returns a handle to stop it. */
  def start(app: MeltApp[F], config: ServerConfig): F[RunningServer[F]]

/** Server configuration shared across SSR and SSG.
  *
  * @param host          bind address (default `"0.0.0.0"`)
  * @param port          listen port (default `3000`)
  * @param template      the HTML shell template for SSR/SSG rendering
  * @param manifest      the Vite manifest for resolving JS/CSS chunks
  * @param basePath      the deployment root path for assets
  * @param cspConfig     optional CSP nonce configuration
  * @param clientDistDir absolute path to the client build output directory
  *                      (Vite `dist` or Scala.js `fastopt`). Used by the
  *                      static file handler to serve JS/CSS assets. `None`
  *                      disables static file serving.
  * @param defaultTitle  default page title used in SSR/SSG rendering
  * @param defaultLang   default HTML `lang` attribute (default: `"en"`)
  * @param outputDir     SSG output directory path; required when running [[SsgGenerator]]
  * @param assetsDir     optional Vite `dist/assets` directory to copy into `outputDir/assets`
  * @param publicDir     optional public directory copied verbatim to `outputDir`
  * @param cleanOutput   when `true`, `outputDir` is deleted and recreated before SSG generation
  * @param quiet         when `true`, per-page SSG progress messages are suppressed
  */
case class ServerConfig(
  host:          String            = "0.0.0.0",
  port:          Int               = 3000,
  template:      Template,
  manifest:      ViteManifest      = ViteManifest.empty,
  basePath:      String            = "",
  cspConfig:     Option[CspConfig] = None,
  clientDistDir: Option[String]    = None,
  defaultTitle:  String            = "",
  defaultLang:   String            = "en",
  outputDir:     Option[String]    = None,
  assetsDir:     Option[String]    = None,
  publicDir:     Option[String]    = None,
  cleanOutput:   Boolean           = true,
  quiet:         Boolean           = false
)

/** A handle to a running server.
  *
  * @param host the bound host address
  * @param port the bound port
  * @param stop a callback to shut down the server
  */
case class RunningServer[F[_]](
  host: String,
  port: Int,
  stop: () => F[Unit]
)
