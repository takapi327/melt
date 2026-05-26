/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.codegen

/** Generates unique variable names during code emission.
  *
  * Used by [[melt.codegen.SpaCodeGen]] and [[melt.emit.SpaEmitter]] to produce
  * non-clashing local `val` names inside the emitted `apply()` body.
  */
final class Counter:
  private var el       = 0
  private var txt      = 0
  private var childIdx = 0
  def nextEl(): String =
    val v = s"_el$el"; el += 1; v
  def nextTxt(): String =
    val v = s"_txt$txt"; txt += 1; v
  def nextChildIdx(): Int =
    val v = childIdx; childIdx += 1; v
