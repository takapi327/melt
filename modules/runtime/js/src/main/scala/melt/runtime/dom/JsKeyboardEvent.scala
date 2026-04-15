/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** JS implementation wrapping `org.scalajs.dom.KeyboardEvent`. */
class JsKeyboardEvent(override val underlying: jsdom.KeyboardEvent) extends JsEvent(underlying) with KeyboardEvent:
  def key: String      = underlying.key
  def code: String     = underlying.code
  def shiftKey: Boolean = underlying.shiftKey
  def ctrlKey: Boolean  = underlying.ctrlKey
  def altKey: Boolean   = underlying.altKey
  def metaKey: Boolean  = underlying.metaKey
  def repeat: Boolean   = underlying.repeat
