/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

/** Selects the kind of Scala code that `meltc` generates from a `.melt` file.
  *
  * See `docs/meltc-ssr-design.md` §3.1 for the full rationale. Put simply:
  *   - `SPA`: generates Scala.js code that manipulates the DOM directly.
  *     This is the classic client-rendered mode and is what the existing
  *     examples (`counter`, `todo-app`, …) rely on.
  *   - `SSR`: generates JVM code that renders components to HTML strings.
  *     Used by server-side rendering (`http4s` SSR example in Phase A) and
  *     by the JVM half of a `crossProject` hydration setup in Phase C.
  *
  * The parser and AST are shared between modes — only code generation
  * differs, via the `CodeGen` trait in `meltc.codegen`.
  */
enum CompileMode:
  case SPA
  case SSR
