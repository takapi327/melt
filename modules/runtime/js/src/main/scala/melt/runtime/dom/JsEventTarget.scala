/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

import org.scalajs.dom as jsdom

/** JS implementation wrapping `org.scalajs.dom.EventTarget`. */
class JsEventTarget(val underlying: jsdom.EventTarget) extends EventTarget
