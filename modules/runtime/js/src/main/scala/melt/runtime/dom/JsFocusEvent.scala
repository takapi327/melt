/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** JS implementation wrapping `org.scalajs.dom.FocusEvent`. */
class JsFocusEvent(override val underlying: jsdom.FocusEvent) extends JsEvent(underlying) with FocusEvent:
  def relatedTarget: Option[EventTarget] =
    Option(underlying.relatedTarget).map(JsEventTarget(_))
