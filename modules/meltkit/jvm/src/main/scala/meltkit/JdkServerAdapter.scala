/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import java.net.InetSocketAddress

import scala.concurrent.{ ExecutionContext, Future }

import com.sun.net.httpserver.HttpServer as JdkHttpServer

/** A [[ServerAdapter]] that uses the JDK built-in `com.sun.net.httpserver.HttpServer`.
  *
  * Fixed to `Future` — no cats-effect or http4s dependency required.
  * For `IO`-based JVM servers, use `meltkit-adapter-http4s` instead.
  */
class JdkServerAdapter(using ec: ExecutionContext) extends ServerAdapter[Future]:

  def start(app: MeltApp[Future], config: ServerConfig): Future[RunningServer[Future]] =
    Future {
      val binding = new JdkHttpBinding(app, config)
      val server  = JdkHttpServer.create(InetSocketAddress(config.host, config.port), 0)
      server.createContext("/", exchange => ec.execute(() => binding.handleExchange(exchange)))
      server.setExecutor(null) // use default executor
      server.start()
      RunningServer(
        host = config.host,
        port = config.port,
        stop = () =>
          Future {
            server.stop(0)
          }
      )
    }
