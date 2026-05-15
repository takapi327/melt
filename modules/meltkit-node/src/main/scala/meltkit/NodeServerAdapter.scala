/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.scalajs.js
import scala.concurrent.{ ExecutionContext, Future }

/** A [[ServerAdapter]] that uses Node.js `http.createServer` directly.
  *
  * Fixed to `Future` — no cats-effect or http4s dependency required.
  * For `IO`-based Node.js servers, use `meltkit-adapter-http4s` instead.
  *
  * Requires an implicit [[ExecutionContext]] for `Future` operations.
  */
class NodeServerAdapter(using ec: ExecutionContext) extends ServerAdapter[Future]:

  def start(app: MeltApp[Future], config: ServerConfig): Future[RunningServer[Future]] =
    Future {
      val binding = new NodeHttpBinding(app, config)
      val server = NodeHttp.createServer { (req, res) =>
        try
          binding.handleRequest(req, res)
        catch
          case _: Throwable =>
            res.writeHead(500, js.Dictionary("Content-Type" -> "text/plain"))
            res.end("Internal Server Error")
      }
      server.listen(config.port, config.host, () => ())
      RunningServer(
        host = config.host,
        port = config.port,
        stop = () => Future {
          server.close(() => ())
        }
      )
    }
