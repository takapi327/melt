/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** JS implementation wrapping `org.scalajs.dom.Event`. */
class JsEvent(val underlying: jsdom.Event) extends Event:
  def `type`:            String              = underlying.`type`
  def preventDefault():  Unit                = underlying.preventDefault()
  def stopPropagation(): Unit                = underlying.stopPropagation()
  def target:            Option[EventTarget] = Option(underlying.target).map(JsEventTarget(_))
  def currentTarget:     Option[EventTarget] = Option(underlying.currentTarget).map(JsEventTarget(_))
