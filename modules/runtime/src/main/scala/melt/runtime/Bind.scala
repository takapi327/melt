/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** DOM binding helpers for reactive values.
  *
  * Each method creates or updates a DOM node/attribute and subscribes to
  * a reactive source (`Var` or `Signal`). Subscriptions are automatically
  * registered with [[Cleanup]] for disposal on component destruction.
  */
object Bind:

  // ── Text bindings ──────────────────────────────────────────────────────

  /** Reactive text binding for [[Var]].
    * Creates a text node, appends it to `parent`, and subscribes so that
    * the text content updates whenever `v` changes.
    */
  def text(v: Var[?], parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(v.now().toString)
    parent.appendChild(node)
    val cancel = v.subscribe(a => node.textContent = a.toString)
    Cleanup.register(cancel)
    node

  /** Reactive text binding for [[Signal]]. */
  def text(signal: Signal[?], parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(signal.now().toString)
    parent.appendChild(node)
    val cancel = signal.subscribe(a => node.textContent = a.toString)
    Cleanup.register(cancel)
    node

  /** Static text binding for non-reactive values (fallback).
    * Creates a text node and appends it to `parent` without any subscription.
    */
  def text(value: Any, parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(value.toString)
    parent.appendChild(node)
    node

  // ── Attribute bindings ─────────────────────────────────────────────────

  /** Reactive attribute binding for [[Var]]. */
  def attr(el: dom.Element, name: String, v: Var[?]): Unit =
    el.setAttribute(name, v.now().toString)
    val cancel = v.subscribe(a => el.setAttribute(name, a.toString))
    Cleanup.register(cancel)

  /** Reactive attribute binding for [[Signal]]. */
  def attr(el: dom.Element, name: String, signal: Signal[?]): Unit =
    el.setAttribute(name, signal.now().toString)
    val cancel = signal.subscribe(a => el.setAttribute(name, a.toString))
    Cleanup.register(cancel)

  /** Static attribute binding (fallback). */
  def attr(el: dom.Element, name: String, value: Any): Unit =
    el.setAttribute(name, value.toString)

  // ── Optional attribute ─────────────────────────────────────────────────

  /** Reactive optional attribute for [[Var]] — removes the attribute when `None`. */
  def optionalAttr[A](el: dom.Element, name: String, v: Var[Option[A]]): Unit =
    optionalAttr(el, name, v.signal)

  /** Reactive optional attribute for [[Signal]] — removes the attribute when `None`. */
  def optionalAttr[A](el: dom.Element, name: String, signal: Signal[Option[A]]): Unit =
    def apply(v: Option[A]): Unit = v match
      case Some(a) => el.setAttribute(name, a.toString)
      case None    => el.removeAttribute(name)
    apply(signal.now())
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  // ── Boolean attribute ──────────────────────────────────────────────────

  /** Reactive boolean attribute for [[Var]] — adds/removes based on value. */
  def booleanAttr(el: dom.Element, name: String, v: Var[Boolean]): Unit =
    booleanAttr(el, name, v.signal)

  /** Reactive boolean attribute for [[Signal]] — adds/removes based on value. */
  def booleanAttr(el: dom.Element, name: String, signal: Signal[Boolean]): Unit =
    def apply(b: Boolean): Unit =
      if b then el.setAttribute(name, "") else el.removeAttribute(name)
    apply(signal.now())
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  // ── Two-way bindings (bind:value) ──────────────────────────────────────

  /** Two-way string binding for `<input>` elements.
    *
    * Sets the input value to `v.now()`, subscribes to `v` for programmatic
    * updates, and listens for `input` events to write back to `v`.
    */
  def inputValue(input: dom.html.Input, v: Var[String]): Unit =
    input.value = v.now()
    val cancelSub = v.subscribe(s => if input.value != s then input.value = s)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => v.set(input.value)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))
