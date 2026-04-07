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

  /** Renders the result of `render()` before `anchor`.
    * Plays an intro transition if the rendered node is an element with one registered.
    * Used for reactive if/else and match expressions.
    */
  def show(render: () => dom.Node, anchor: dom.Node): Unit =
    val parent  = anchor.parentNode
    val current = render()
    parent.insertBefore(current, anchor)
    current match
      case el: dom.Element if TransitionBridge.hasIn(el) => TransitionBridge.playIn(el)
      case _                                             =>

  /** Reactive conditional rendering for [[Var]]. Re-renders when `v` changes.
    * Plays intro on the entering element and outro on the leaving element.
    */
  def show(v: Var[?], render: Any => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var current: dom.Node = render(v.now())
    parent.insertBefore(current, anchor)
    current match
      case el: dom.Element if TransitionBridge.hasIn(el) => TransitionBridge.playIn(el)
      case _                                             => playGlobalTransitions(current, intro = true)
    val cancel = v.subscribe { a =>
      val next = render(a)
      val old  = current
      current = next
      parent.insertBefore(next, anchor)
      next match
        case el: dom.Element if TransitionBridge.hasIn(el) =>
          TransitionBridge.playIn(el)
          playGlobalTransitions(next, intro = true)
        case _ =>
          playGlobalTransitions(next, intro = true)
      old match
        case el: dom.Element if TransitionBridge.hasOut(el) =>
          playGlobalTransitions(old, intro = false)
          TransitionBridge.playOut(el, () => Option(el.parentNode).foreach(_.removeChild(el)))
        case _ =>
          playGlobalTransitions(old, intro = false)
          parent.removeChild(old)
    }
    Cleanup.register(cancel)

  /** Reactive conditional rendering for [[Signal]]. Re-renders when signal changes.
    * Plays intro on the entering element and outro on the leaving element.
    */
  def show(signal: Signal[?], render: Any => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    var current: dom.Node = render(signal.now())
    parent.insertBefore(current, anchor)
    current match
      case el: dom.Element if TransitionBridge.hasIn(el) => TransitionBridge.playIn(el)
      case _                                             => playGlobalTransitions(current, intro = true)
    val cancel = signal.subscribe { a =>
      val next = render(a)
      val old  = current
      current = next
      parent.insertBefore(next, anchor)
      next match
        case el: dom.Element if TransitionBridge.hasIn(el) =>
          TransitionBridge.playIn(el)
          playGlobalTransitions(next, intro = true)
        case _ =>
          playGlobalTransitions(next, intro = true)
      old match
        case el: dom.Element if TransitionBridge.hasOut(el) =>
          playGlobalTransitions(old, intro = false)
          TransitionBridge.playOut(el, () => Option(el.parentNode).foreach(_.removeChild(el)))
        case _ =>
          playGlobalTransitions(old, intro = false)
          parent.removeChild(old)
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

  /** Keyed list rendering with node reuse. Reorders and adds/removes nodes efficiently.
    *
    * When list-item elements have `_meltFlip` set (via `animate:flip`), a FLIP
    * position snapshot is taken before the DOM mutation and played back afterwards.
    */
  def each[A, K](
    source:   Var[? <: Iterable[A]],
    keyFn:    A => K,
    renderFn: A => dom.Node,
    anchor:   dom.Node
  ): Unit =
    val parent  = anchor.parentNode
    var nodeMap = mutable.LinkedHashMap.empty[K, dom.Node]

    def rebuild(items: Iterable[A]): Unit =
      // Snapshot positions of flip-marked elements before mutation
      val flipEls = nodeMap.values.collect {
        case el: dom.Element if isFlipMarked(el) => el
      }
      val before  = if flipEls.nonEmpty then melt.runtime.animate.Flip.snapshot(flipEls) else Map.empty
      val newKeys = items.map(keyFn).toSet
      val oldKeys = nodeMap.keySet.toSet
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
      // Play FLIP on surviving elements
      if before.nonEmpty then
        val survivors = newMap.values.collect { case el: dom.Element if isFlipMarked(el) => el }
        melt.runtime.animate.Flip.play(survivors, before)

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

    def rebuild(items: Iterable[A]): Unit =
      val flipEls = nodeMap.values.collect {
        case el: dom.Element if isFlipMarked(el) => el
      }
      val before  = if flipEls.nonEmpty then melt.runtime.animate.Flip.snapshot(flipEls) else Map.empty
      val newKeys = items.map(keyFn).toSet
      val oldKeys = nodeMap.keySet.toSet
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
      if before.nonEmpty then
        val survivors = newMap.values.collect { case el: dom.Element if isFlipMarked(el) => el }
        melt.runtime.animate.Flip.play(survivors, before)

    rebuild(source.now())
    val cancel = source.subscribe(items => rebuild(items.asInstanceOf[Iterable[A]]))
    Cleanup.register(cancel)

  /** Returns `true` if the element has the `animate:flip` marker (`_meltFlip`). */
  private def isFlipMarked(el: dom.Element): Boolean =
    !scalajs.js.isUndefined(el.asInstanceOf[scalajs.js.Dynamic]._meltFlip)

  /** Returns `true` if the element has the `|global` modifier (`_meltGlobal`). */
  private def isGlobalMarked(el: dom.Element): Boolean =
    !scalajs.js.isUndefined(el.asInstanceOf[scalajs.js.Dynamic].selectDynamic("_meltGlobal"))

  /** Recursively plays intro/outro transitions on elements marked with `|global`
    * within the given node subtree.  Called by [[show]] when a parent block
    * enters or leaves the DOM so that globally-marked child elements animate too.
    */
  private def playGlobalTransitions(node: dom.Node, intro: Boolean): Unit =
    node match
      case el: dom.Element =>
        if isGlobalMarked(el) then
          if intro && TransitionBridge.hasIn(el) then TransitionBridge.playIn(el)
          if !intro && TransitionBridge.hasOut(el) then TransitionBridge.playOut(el, () => ())
        // Recurse into children
        (0 until el.children.length).foreach { i =>
          playGlobalTransitions(el.children(i), intro)
        }
      case _ =>

  // ── Raw HTML insertion ────────────────────────────────────────────────

  /** Sets innerHTML reactively. **Warning:** XSS risk — only use with trusted content. */
  def html(el: dom.Element, content: Var[String]): Unit =
    el.innerHTML = content.now()
    val cancel = content.subscribe(s => el.innerHTML = s)
    Cleanup.register(cancel)

  def html(el: dom.Element, content: Signal[String]): Unit =
    el.innerHTML = content.now()
    val cancel = content.subscribe(s => el.innerHTML = s)
    Cleanup.register(cancel)

  def html(el: dom.Element, content: String): Unit =
    el.innerHTML = content

  // ── Action binding (use: directive) ───────────────────────────────────

  /** Applies an action to an element with a static parameter. */
  def action[P](el: dom.Element, act: Action[P], param: P): Unit =
    val cleanup = act(el, param)
    Cleanup.register(cleanup)

  /** Applies an action with a reactive Var parameter. Re-applies on change. */
  def action[P](el: dom.Element, act: Action[P], param: Var[P]): Unit =
    var prevCleanup: () => Unit = act(el, param.now())
    val cancel = param.subscribe { p =>
      prevCleanup()
      prevCleanup = act(el, p)
    }
    Cleanup.register(() => { prevCleanup(); cancel() })

  /** Applies an action with a reactive Signal parameter. */
  def action[P](el: dom.Element, act: Action[P], param: Signal[P]): Unit =
    var prevCleanup: () => Unit = act(el, param.now())
    val cancel = param.subscribe { p =>
      prevCleanup()
      prevCleanup = act(el, p)
    }
    Cleanup.register(() => { prevCleanup(); cancel() })
