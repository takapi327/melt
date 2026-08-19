/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

import components.App
import generated.AssetManifest
import meltkit.*

/** MeltKit[Future] SSR + Hydration server (Undertow) styled with Tailwind CSS.
  *
  * The component is JVM-safe (State + Tailwind utility classes, no `@JSImport`),
  * so the same `App.melt` cross-compiles: the server renders the Tailwind-styled
  * HTML via SSR and the client hydrates it.
  *
  * The Tailwind CSS is generated ahead of time by the Tailwind CLI (scanning the
  * `.melt` sources) and inlined into the template `<head>` at startup — no bundler
  * on the server side.
  *
  * {{{
  * sbt "tailwind-ssrJS/fastLinkJS"                                   # hydration bundle
  * pnpm --dir tailwind-ssr-server install
  * pnpm --dir tailwind-ssr-server exec tailwindcss \
  *   -i tailwind.css -o src/main/resources/generated.css            # build CSS
  * sbt "tailwind-ssr-server/run"                                    # http://localhost:9093
  * }}}
  */
object Server:

  def main(args: Array[String]): Unit =
    val app = MeltKit[Future]()

    app.get("") { ctx =>
      Future.successful(ctx.render(App(App.Props(initial = 0))))
    }

    val rawTemplate = scala.io.Source.fromResource("index.html").mkString
    val css         = Try(scala.io.Source.fromResource("generated.css").mkString).getOrElse("")
    val template    = rawTemplate.replace("%tailwind%", s"<style>$css</style>")

    UndertowServer
      .builder(app)
      .withHost("0.0.0.0")
      .withPort(9093)
      .withTemplate(template)
      .withManifest(AssetManifest.manifest)
      .withClientDistDir(AssetManifest.clientDistDir)
      .start()
      .foreach(server => println(s"tailwind-ssr running on http://localhost:${ server.port }"))

    Thread.currentThread().join()
