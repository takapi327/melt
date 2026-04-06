/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Error boundary component that catches exceptions during child rendering
  * and displays a fallback UI.
  *
  * {{{
  * <Boundary fallback={(e, retry) => <p>Error: {e.getMessage} <button onclick={_ => retry()}>Retry</button></p>}>
  *   <RiskyComponent />
  * </Boundary>
  * }}}
  */
object Boundary:

  case class Props(
    children: () => dom.Element,
    fallback: (Throwable, () => Unit) => dom.Element,
    onError:  Throwable => Unit = _ => ()
  )

  def create(props: Props): dom.Element =
    val container = dom.document.createElement("div")

    def render(): Unit =
      while container.firstChild != null do container.removeChild(container.firstChild)
      try
        val child = props.children()
        container.appendChild(child)
      catch
        case e: Exception =>
          props.onError(e)
          val fb = props.fallback(e, () => render())
          container.appendChild(fb)
        case e: scalajs.js.JavaScriptException =>
          val wrapped = new RuntimeException(e.getMessage)
          props.onError(wrapped)
          val fb = props.fallback(wrapped, () => render())
          container.appendChild(fb)

    render()
    container
