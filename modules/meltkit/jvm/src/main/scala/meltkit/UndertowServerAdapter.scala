/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }

import io.undertow.server.HttpHandler
import io.undertow.Undertow

/** A [[ServerAdapter]] backed by Undertow.
  *
  * Production-ready NIO server with no cats-effect dependency.
  * Fixed to `Future`. For `IO`-based JVM servers, use `meltkit-adapter-http4s` instead.
  */
class UndertowServerAdapter(using ec: ExecutionContext) extends ServerAdapter[Future]:

  def start(app: ServerMeltKitPlatform[Future], config: ServerConfig): Future[RunningServer[Future]] =
    Future {
      val binding = new UndertowHttpBinding(app, config)
      val handler: HttpHandler = exchange =>
        exchange.dispatch(new Runnable:
          def run(): Unit = binding.handleExchange(exchange))
      val server = Undertow
        .builder()
        .addHttpListener(config.port, config.host)
        .setHandler(handler)
        .build()
      silenceJboss()
      server.start()
      val running = RunningServer(
        host = config.host,
        port = config.port,
        stop = () => Future { server.stop() }
      )
      Runtime.getRuntime.addShutdownHook(new Thread(() => server.stop()))
      running
    }

  private def silenceJboss(): Unit =
    java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.WARNING)
