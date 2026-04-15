/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** JS implementation wrapping `org.scalajs.dom.Element`. */
class JsElement(val underlying: jsdom.Element) extends Element:
  def textContent: String                        = underlying.textContent
  def tagName: String                            = underlying.tagName
  def getAttribute(name: String): Option[String] = Option(underlying.getAttribute(name))
  def setAttribute(name: String, value: String): Unit = underlying.setAttribute(name, value)
  def removeAttribute(name: String): Unit        = underlying.removeAttribute(name)
