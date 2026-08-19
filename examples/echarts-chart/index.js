// Entry point. `@scala-js/vite-plugin-scalajs` resolves the `scalajs:` prefix to
// the Scala.js linker output; importing the main module runs `Main.main`
// (scalaJSUseMainModuleInitializer := true).
import 'scalajs:main.js'
