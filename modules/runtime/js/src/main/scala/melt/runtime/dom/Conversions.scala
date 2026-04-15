/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** Conversions between `org.scalajs.dom` types and Melt DOM abstractions. */
object Conversions:

  /** Wraps a raw JS event into the most specific Melt event type. */
  def wrap(e: jsdom.Event): Event = e match
    case me: jsdom.MouseEvent    => JsMouseEvent(me)
    case ke: jsdom.KeyboardEvent => JsKeyboardEvent(ke)
    case ie: jsdom.InputEvent    => JsInputEvent(ie)
    case fe: jsdom.FocusEvent    => JsFocusEvent(fe)
    case _                       => JsEvent(e)

  /** Wraps a raw JS element into the most specific Melt element type. */
  def wrapElement(e: jsdom.Element): Element = e match
    case ie: jsdom.html.Input => JsInputElement(ie)
    case _                    => JsElement(e)

  /** Unwraps a Melt element back to its underlying `org.scalajs.dom.Element`. */
  def unwrap(e: Element): jsdom.Element = e match
    case ie: JsInputElement => ie.underlying
    case je: JsElement      => je.underlying
