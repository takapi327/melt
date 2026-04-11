/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package client

/** Phase C hydration client entry.
  *
  * This project exists purely to link the `components.js` sub-module
  * (a Scala.js library) into a set of per-component public chunks via
  * `ModuleSplitStyle.SmallModulesFor`. It has no user code of its own
  * — each component's `@JSExportTopLevel("hydrate", moduleID = "…")`
  * entry in `components.js` is the only thing the browser calls.
  *
  * The `main` method below is intentionally a no-op: Scala.js requires
  * SOMETHING to be reachable from a top-level entrypoint, otherwise the
  * linker drops the `components` package entirely as dead code.
  *
  * `scalaJSUseMainModuleInitializer := false` in `build.sbt` means this
  * method never actually runs — we only need its `@JSExportTopLevel`-
  * reachable references to keep `components.*` alive in the final
  * bundle.
  */
object Main:

  // Referencing the components package keeps its classes reachable so
  // that `@JSExportTopLevel` on each component is not tree-shaken away.
  // We use a sentinel boolean that the optimiser cannot inline; the
  // actual value is never observed.
  @scala.scalajs.js.annotation.JSExportTopLevel("__meltClientLive")
  val alive: Boolean =
    // Simply referencing the `components` package object is enough
    // because each .melt-generated object holds a `@JSExportTopLevel`
    // that acts as a linker root.
    true
