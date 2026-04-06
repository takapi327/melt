/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Error boundary component that catches exceptions during child rendering
  * and displays a fallback UI.
  */
object Boundary:

  case class Props(
    children: () => dom.Element,
    fallback: (Throwable, () => Unit) => dom.Element,
    onError:  Throwable => Unit = _ => ()
  )

  def create(props: Props): dom.Element =
    val container = dom.document.createElement("div")
    var childCleanups: List[() => Unit] = Nil

    def render(): Unit =
      // Clean up previous child subscriptions
      Cleanup.runAll(childCleanups)
      childCleanups = Nil
      while container.firstChild != null do container.removeChild(container.firstChild)

      Cleanup.pushScope()
      try
        val child = props.children()
        container.appendChild(child)
        childCleanups = Cleanup.popScope()
      catch
        // JavaScriptException must come before Exception (it extends Exception)
        case e: scalajs.js.JavaScriptException =>
          childCleanups = Cleanup.popScope()
          val wrapped = new RuntimeException(e.getMessage)
          props.onError(wrapped)
          val fb = props.fallback(wrapped, () => render())
          container.appendChild(fb)
        case e: Exception =>
          childCleanups = Cleanup.popScope()
          props.onError(e)
          val fb = props.fallback(e, () => render())
          container.appendChild(fb)

    render()
    container
