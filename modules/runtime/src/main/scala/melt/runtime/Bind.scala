/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

/** DOM binding helpers for reactive values.
  *
  * Each method creates or updates a DOM node/attribute and subscribes to
  * a reactive source (`Var` or `Signal`). Subscriptions are automatically
  * registered with [[Cleanup]] for disposal on component destruction.
  */
object Bind:

  // ── Text bindings ──────────────────────────────────────────────────────

  def text(v: Var[?], parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(v.now().toString)
    parent.appendChild(node)
    val cancel = v.subscribe(a => node.textContent = a.toString)
    Cleanup.register(cancel)
    node

  def text(signal: Signal[?], parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(signal.now().toString)
    parent.appendChild(node)
    val cancel = signal.subscribe(a => node.textContent = a.toString)
    Cleanup.register(cancel)
    node

  def text(value: Any, parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(value.toString)
    parent.appendChild(node)
    node

  // ── Attribute bindings ─────────────────────────────────────────────────

  def attr(el: dom.Element, name: String, v: Var[?]): Unit =
    el.setAttribute(name, v.now().toString)
    val cancel = v.subscribe(a => el.setAttribute(name, a.toString))
    Cleanup.register(cancel)

  def attr(el: dom.Element, name: String, signal: Signal[?]): Unit =
    el.setAttribute(name, signal.now().toString)
    val cancel = signal.subscribe(a => el.setAttribute(name, a.toString))
    Cleanup.register(cancel)

  /** Static attribute binding (fallback).
    * Boolean values set/remove the attribute and the DOM property for
    * properties like `checked`, `disabled`, `selected`.
    */
  def attr(el: dom.Element, name: String, value: Any): Unit =
    value match
      case b: Boolean =>
        if b then el.setAttribute(name, "") else el.removeAttribute(name)
        // Set DOM property directly for boolean properties
        el.asInstanceOf[scalajs.js.Dynamic].updateDynamic(name)(b)
      case _ =>
        el.setAttribute(name, value.toString)

  // ── Optional attribute ─────────────────────────────────────────────────

  def optionalAttr[A](el: dom.Element, name: String, v: Var[Option[A]]): Unit =
    optionalAttr(el, name, v.signal)

  def optionalAttr[A](el: dom.Element, name: String, signal: Signal[Option[A]]): Unit =
    def apply(v: Option[A]): Unit = v match
      case Some(a) => el.setAttribute(name, a.toString)
      case None    => el.removeAttribute(name)
    apply(signal.now())
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  // ── Boolean attribute ──────────────────────────────────────────────────

  def booleanAttr(el: dom.Element, name: String, v: Var[Boolean]): Unit =
    booleanAttr(el, name, v.signal)

  def booleanAttr(el: dom.Element, name: String, signal: Signal[Boolean]): Unit =
    def apply(b: Boolean): Unit =
      if b then el.setAttribute(name, "") else el.removeAttribute(name)
    apply(signal.now())
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  // ── Two-way bindings ───────────────────────────────────────────────────

  /** Two-way string binding for `<input>`. */
  def inputValue(input: dom.html.Input, v: Var[String]): Unit =
    input.value = v.now()
    val cancelSub = v.subscribe(s => if input.value != s then input.value = s)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => v.set(input.value)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))

  /** Two-way Int binding for `<input type="number">`. */
  def inputInt(input: dom.html.Input, v: Var[Int]): Unit =
    input.value = v.now().toString
    val cancelSub = v.subscribe(n => { val s = n.toString; if input.value != s then input.value = s })
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => input.value.toIntOption.foreach(v.set)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))

  /** Two-way Double binding for `<input type="number">`. */
  def inputDouble(input: dom.html.Input, v: Var[Double]): Unit =
    input.value = v.now().toString
    val cancelSub = v.subscribe(n => { val s = n.toString; if input.value != s then input.value = s })
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => input.value.toDoubleOption.foreach(v.set)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))

  /** Two-way checkbox binding. */
  def inputChecked(input: dom.html.Input, v: Var[Boolean]): Unit =
    input.checked = v.now()
    val cancelSub = v.subscribe(b => if input.checked != b then input.checked = b)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => v.set(input.checked)
    input.addEventListener("change", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("change", listener))

  /** Two-way radio group binding. */
  def radioGroup(input: dom.html.Input, v: Var[String], value: String): Unit =
    input.checked = v.now() == value
    val cancelSub = v.subscribe(s => input.checked = s == value)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => if input.checked then v.set(value)
    input.addEventListener("change", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("change", listener))

  /** Two-way checkbox group binding (list of selected values). */
  def checkboxGroup(input: dom.html.Input, v: Var[List[String]], value: String): Unit =
    input.checked = v.now().contains(value)
    val cancelSub = v.subscribe(list => input.checked = list.contains(value))
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) =>
      if input.checked then { if !v.now().contains(value) then v.update(_ :+ value) }
      else v.update(_.filterNot(_ == value))
    input.addEventListener("change", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("change", listener))

  // ── Class toggle (class:name={expr}) ───────────────────────────────────

  def classToggle(el: dom.Element, className: String, v: Var[Boolean]): Unit =
    classToggle(el, className, v.signal)

  def classToggle(el: dom.Element, className: String, signal: Signal[Boolean]): Unit =
    def apply(b: Boolean): Unit =
      if b then el.classList.add(className) else el.classList.remove(className)
    apply(signal.now())
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  def classToggle(el: dom.Element, className: String, value: Boolean): Unit =
    if value then el.classList.add(className) else el.classList.remove(className)

  // ── Style binding (style:property={expr}) ──────────────────────────────

  def style(el: dom.Element, property: String, v: Var[?]): Unit =
    el.asInstanceOf[dom.html.Element].style.setProperty(property, v.now().toString)
    val cancel = v.subscribe(a => el.asInstanceOf[dom.html.Element].style.setProperty(property, a.toString))
    Cleanup.register(cancel)

  def style(el: dom.Element, property: String, signal: Signal[?]): Unit =
    el.asInstanceOf[dom.html.Element].style.setProperty(property, signal.now().toString)
    val cancel =
      signal.subscribe(a => el.asInstanceOf[dom.html.Element].style.setProperty(property, a.toString))
    Cleanup.register(cancel)

  def style(el: dom.Element, property: String, value: Any): Unit =
    el.asInstanceOf[dom.html.Element].style.setProperty(property, value.toString)

  // ── Conditional rendering (if/else, match) ─────────────────────────────

  /** Renders the result of `render()` before `anchor`, replacing the previous result on each call.
    * Used for reactive if/else and match expressions.
    */
  def show(render: () => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var current: dom.Node = render()
    parent.insertBefore(current, anchor)

  /** Reactive conditional rendering for [[Var]]. Re-renders when `v` changes. */
  def show(v: Var[?], render: Any => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var current: dom.Node = render(v.now())
    parent.insertBefore(current, anchor)
    val cancel = v.subscribe { a =>
      val next = render(a)
      parent.replaceChild(next, current)
      current = next
    }
    Cleanup.register(cancel)

  /** Reactive conditional rendering for [[Signal]]. Re-renders when signal changes. */
  def show(signal: Signal[?], render: Any => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var current: dom.Node = render(signal.now())
    parent.insertBefore(current, anchor)
    val cancel = signal.subscribe { a =>
      val next = render(a)
      parent.replaceChild(next, current)
      current = next
    }
    Cleanup.register(cancel)

  // ── List rendering ────────────────────────────────────────────────────

  /** Renders a list of items before `anchor`. Rebuilds on each change.
    * Used for `{items.map(renderFn)}` in templates.
    */
  def list[A](source: Var[? <: Iterable[A]], renderFn: A => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var nodes  = mutable.ListBuffer.empty[dom.Node]

    def rebuild(items: Iterable[A]): Unit =
      nodes.foreach(n => parent.removeChild(n))
      nodes.clear()
      items.foreach { item =>
        val node = renderFn(item)
        parent.insertBefore(node, anchor)
        nodes += node
      }

    rebuild(source.now())
    val cancel = source.subscribe(items => rebuild(items.asInstanceOf[Iterable[A]]))
    Cleanup.register(cancel)

  def list[A](source: Signal[? <: Iterable[A]], renderFn: A => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var nodes  = mutable.ListBuffer.empty[dom.Node]

    def rebuild(items: Iterable[A]): Unit =
      nodes.foreach(n => parent.removeChild(n))
      nodes.clear()
      items.foreach { item =>
        val node = renderFn(item)
        parent.insertBefore(node, anchor)
        nodes += node
      }

    rebuild(source.now())
    val cancel = source.subscribe(items => rebuild(items.asInstanceOf[Iterable[A]]))
    Cleanup.register(cancel)

  /** Keyed list rendering with node reuse. Reorders and adds/removes nodes efficiently. */
  def each[A, K](
    source:   Var[? <: Iterable[A]],
    keyFn:    A => K,
    renderFn: A => dom.Node,
    anchor:   dom.Node
  ): Unit =
    val parent  = anchor.parentNode
    var nodeMap = mutable.LinkedHashMap.empty[K, dom.Node]

    def rebuild(items: Iterable[A]): Unit =
      val newKeys = items.map(keyFn).toSet
      val oldKeys = nodeMap.keySet
      // Remove nodes whose keys no longer exist
      (oldKeys -- newKeys).foreach { k =>
        nodeMap.get(k).foreach(n => parent.removeChild(n))
        nodeMap -= k
      }
      // Add/reorder
      val newMap = mutable.LinkedHashMap.empty[K, dom.Node]
      items.foreach { item =>
        val k    = keyFn(item)
        val node = nodeMap.getOrElse(k, renderFn(item))
        parent.insertBefore(node, anchor)
        newMap(k) = node
      }
      nodeMap = newMap

    rebuild(source.now())
    val cancel = source.subscribe(items => rebuild(items.asInstanceOf[Iterable[A]]))
    Cleanup.register(cancel)

  def each[A, K](
    source:   Signal[? <: Iterable[A]],
    keyFn:    A => K,
    renderFn: A => dom.Node,
    anchor:   dom.Node
  ): Unit =
    val parent  = anchor.parentNode
    var nodeMap = mutable.LinkedHashMap.empty[K, dom.Node]

    def rebuildS(items: Iterable[A]): Unit =
      val newKeys = items.map(keyFn).toSet
      val oldKeys = nodeMap.keySet
      (oldKeys -- newKeys).foreach { k =>
        nodeMap.get(k).foreach(n => parent.removeChild(n))
        nodeMap -= k
      }
      val newMap = mutable.LinkedHashMap.empty[K, dom.Node]
      items.foreach { item =>
        val k    = keyFn(item)
        val node = nodeMap.getOrElse(k, renderFn(item))
        parent.insertBefore(node, anchor)
        newMap(k) = node
      }
      nodeMap = newMap

    rebuildS(source.now())
    val cancel = source.subscribe(items => rebuildS(items.asInstanceOf[Iterable[A]]))
    Cleanup.register(cancel)
