/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** JS implementation wrapping `org.scalajs.dom.MouseEvent`. */
class JsMouseEvent(override val underlying: jsdom.MouseEvent) extends JsEvent(underlying) with MouseEvent:
  def clientX: Double  = underlying.clientX
  def clientY: Double  = underlying.clientY
  def button: Int      = underlying.button
  def shiftKey: Boolean = underlying.shiftKey
  def ctrlKey: Boolean  = underlying.ctrlKey
  def altKey: Boolean   = underlying.altKey
  def metaKey: Boolean  = underlying.metaKey
