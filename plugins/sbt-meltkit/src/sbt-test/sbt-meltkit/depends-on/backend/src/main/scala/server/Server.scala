package server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import components.App
import generated.AssetManifest
import meltkit.*

object Server:
  def main(args: Array[String]): Unit =
    // References the JVM side of `shared.Const` via backendDependsOn(sharedJvm).
    val app = MeltKit[Future]()
    app.get("") { ctx => Future.successful(ctx.render(App())) }
    UndertowServer
      .builder(app)
      .withPort(9100 + shared.Const.value)
      .withTemplate("<!doctype html><html><head>%melt.head%</head><body>%melt.body%</body></html>")
      .withManifest(AssetManifest.manifest)
      .withClientDistDir(AssetManifest.clientDistDir)
      .start()
      .foreach(s => println(s"running on ${ s.port }"))
