/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import melt.runtime.dom.Element

/** A reusable element action attached via `use:actionName={param}`.
  *
  * An action receives an element and an optional parameter, performs setup,
  * and returns a cleanup function.
  *
  * {{{
  * val tooltip = Action[String] { (el, text) =>
  *   el.setAttribute("title", text)
  *   () => el.removeAttribute("title")
  * }
  * // In template: <div use:tooltip={"Help text"}>...</div>
  * }}}
  */
trait Action[P]:
  def apply(el: Element, param: P): () => Unit

object Action:
  /** Creates an action from a setup function that returns a cleanup function. */
  def apply[P](f: (Element, P) => (() => Unit)): Action[P] =
    new Action[P]:
      def apply(el: Element, param: P): () => Unit = f(el, param)

  /** Creates a parameterless action. */
  def simple(f: Element => (() => Unit)): Action[Unit] =
    new Action[Unit]:
      def apply(el: Element, param: Unit): () => Unit = f(el)
