/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

/** An opaque wrapper for a Scala expression extracted from a `.melt` template.
  *
  * We intentionally do not parse Scala — the expression is kept as a raw
  * string so that the Scala compiler handles type-checking at the final
  * `.scala` compilation step.
  *
  * The wrapper exists to distinguish "a Scala expression (String)" from
  * "an emitted code fragment (String)" in the IR data structures, preventing
  * accidental mix-ups at compile time.
  */
opaque type ScalaExpr = String

object ScalaExpr:
  def apply(code: String):             ScalaExpr = code
  extension (e:   ScalaExpr) def code: String    = e
