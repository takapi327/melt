/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

/** JVM no-op implementations of all element types.
  *
  * SSR renders to strings, so these are never instantiated at runtime.
  * They exist only for type compatibility in shared code.
  */
class NoOpNode extends Node:
  def textContent: String = ""

class NoOpElement extends Element:
  def textContent:                                  String         = ""
  def tagName:                                      String         = ""
  def getAttribute(name:    String):                Option[String] = None
  def setAttribute(name:    String, value: String): Unit           = ()
  def removeAttribute(name: String):                Unit           = ()

class NoOpInputElement extends NoOpElement with InputElement:
  private var _value:        String  = ""
  private var _checked:      Boolean = false
  def value:                 String  = _value
  def value_=(v:   String):  Unit    = _value   = v
  def checked:               Boolean = _checked
  def checked_=(v: Boolean): Unit    = _checked = v
