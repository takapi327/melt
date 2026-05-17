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

/** Server configuration.
  *
  * @param host          bind address (default `"0.0.0.0"`)
  * @param port          listen port (default `3000`)
  * @param template      the HTML shell template for SSR rendering
  * @param manifest      the Vite manifest for resolving JS/CSS chunks
  * @param basePath      the deployment root path for assets (default `"/assets"`)
  * @param cspConfig     optional CSP nonce configuration
  * @param clientDistDir absolute path to the client build output directory
  *                      (Vite `dist` or Scala.js `fastopt`). Used by the
  *                      static file handler to serve JS/CSS assets. `None`
  *                      disables static file serving.
  */
case class ServerConfig(
  host:          String            = "0.0.0.0",
  port:          Int               = 3000,
  template:      Template,
  manifest:      ViteManifest,
  basePath:      String            = "/assets",
  cspConfig:     Option[CspConfig] = None,
  clientDistDir: Option[String]    = None
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
