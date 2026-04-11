/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.collection.mutable

import org.scalajs.dom

import melt.runtime.animate.{ AnimateEngine, AnimateFn, AnimateInfo, AnimateParams }

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

  def text(value: String, parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(value)
    parent.appendChild(node)
    node

  def text(value: Int, parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(value.toString)
    parent.appendChild(node)
    node

  // ── Attribute bindings ─────────────────────────────────────────────────

  /** Compile-time guard: Var[Boolean] must use booleanAttr, not attr.
    *
    * `Bind.attr` calls `.toString` which produces `"false"` — an HTML boolean
    * attribute set to any non-empty string means the attribute is **present**,
    * so `disabled="false"` keeps the element disabled.
    * Use `Bind.booleanAttr` instead, which removes the attribute when false.
    */
  @scala.annotation.targetName("attrBooleanVar")
  inline def attr(el: dom.Element, name: String, v: Var[Boolean]): Unit =
    scala.compiletime.error(
      "Use Bind.booleanAttr instead of Bind.attr for Var[Boolean]. " +
        "Bind.attr calls .toString() which produces \"false\" — HTML boolean attributes " +
        "must be removed (not set to \"false\") to mean false."
    )

  /** Compile-time guard: Signal[Boolean] must use booleanAttr, not attr. */
  @scala.annotation.targetName("attrBooleanSignal")
  inline def attr(el: dom.Element, name: String, signal: Signal[Boolean]): Unit =
    scala.compiletime.error(
      "Use Bind.booleanAttr instead of Bind.attr for Signal[Boolean]. " +
        "Bind.attr calls .toString() which produces \"false\" — HTML boolean attributes " +
        "must be removed (not set to \"false\") to mean false."
    )

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

  def booleanAttr(el: dom.Element, name: String, value: Boolean): Unit =
    if value then el.setAttribute(name, "") else el.removeAttribute(name)
    el.asInstanceOf[scalajs.js.Dynamic].updateDynamic(name)(value)

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
          Lifecycle.destroyTree(el)
          TransitionBridge.playOut(el, () => Option(el.parentNode).foreach(_.removeChild(el)))
        case el: dom.Element =>
          playGlobalTransitions(old, intro = false)
          parent.removeChild(old)
          Lifecycle.destroyTree(el)
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
          Lifecycle.destroyTree(el)
          TransitionBridge.playOut(el, () => Option(el.parentNode).foreach(_.removeChild(el)))
        case el: dom.Element =>
          playGlobalTransitions(old, intro = false)
          parent.removeChild(old)
          Lifecycle.destroyTree(el)
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
      nodes.foreach { n =>
        parent.removeChild(n)
        n match
          case el: dom.Element => Lifecycle.destroyTree(el)
          case _               =>
      }
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
      nodes.foreach { n =>
        parent.removeChild(n)
        n match
          case el: dom.Element => Lifecycle.destroyTree(el)
          case _               =>
      }
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
    * When list-item elements have `_meltAnimateFn` set (via `animate:` directive),
    * a position snapshot is taken before the DOM mutation and the animate function
    * is called afterwards to produce the animation.
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
      // Snapshot positions of animate-marked elements before mutation
      val animEls = nodeMap.values.collect {
        case el: dom.Element if isAnimateMarked(el) => el
      }
      val before  = if animEls.nonEmpty then AnimateEngine.snapshot(animEls) else Map.empty
      val newKeys = items.map(keyFn).toSet
      val oldKeys = nodeMap.keySet.toSet
      // Remove nodes whose keys no longer exist; destroy their subscriptions too
      (oldKeys -- newKeys).foreach { k =>
        nodeMap.get(k).foreach { n =>
          parent.removeChild(n)
          n match
            case el: dom.Element => Lifecycle.destroyTree(el)
            case _               =>
        }
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
      // Play animations on surviving animate-marked elements
      if before.nonEmpty then
        val survivors = newMap.values.collect { case el: dom.Element if isAnimateMarked(el) => el }
        playAnimations(survivors, before)

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
      val animEls = nodeMap.values.collect {
        case el: dom.Element if isAnimateMarked(el) => el
      }
      val before  = if animEls.nonEmpty then AnimateEngine.snapshot(animEls) else Map.empty
      val newKeys = items.map(keyFn).toSet
      val oldKeys = nodeMap.keySet.toSet
      // Remove nodes whose keys no longer exist; destroy their subscriptions too
      (oldKeys -- newKeys).foreach { k =>
        nodeMap.get(k).foreach { n =>
          parent.removeChild(n)
          n match
            case el: dom.Element => Lifecycle.destroyTree(el)
            case _               =>
        }
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
        val survivors = newMap.values.collect { case el: dom.Element if isAnimateMarked(el) => el }
        playAnimations(survivors, before)

    rebuild(source.now())
    val cancel = source.subscribe(items => rebuild(items.asInstanceOf[Iterable[A]]))
    Cleanup.register(cancel)

  /** Returns `true` if the element has an animate function stored (`_meltAnimateFn`). */
  private def isAnimateMarked(el: dom.Element): Boolean =
    !scalajs.js.isUndefined(el.asInstanceOf[scalajs.js.Dynamic].selectDynamic("_meltAnimateFn"))

  /** Calls each element's stored [[AnimateFn]] with its old/new position and runs the result. */
  private def playAnimations(
    els:    Iterable[dom.Element],
    before: Map[dom.Element, dom.DOMRect]
  ): Unit =
    els.foreach { el =>
      before.get(el).foreach { fromRect =>
        val toRect = el.getBoundingClientRect()
        val dyn    = el.asInstanceOf[scalajs.js.Dynamic]
        // Guard explicitly even though callers already checked isAnimateMarked,
        // so that a race or incorrect call cannot produce a ClassCastException.
        val fnRaw = dyn.selectDynamic("_meltAnimateFn")
        if !scalajs.js.isUndefined(fnRaw) then
          val fn     = fnRaw.asInstanceOf[AnimateFn]
          val params =
            val p = dyn.selectDynamic("_meltAnimateParams")
            if scalajs.js.isUndefined(p) then AnimateParams() else p.asInstanceOf[AnimateParams]
          val info   = AnimateInfo(from = fromRect, to = toRect)
          val config = fn(el, info, params)
          AnimateEngine.run(el, config)
      }
    }

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

  /** Sets innerHTML reactively from a [[TrustedHtml]] value.
    *
    * Requires [[TrustedHtml]] instead of a plain `String` or `Var[String]` to
    * make XSS-prone call sites visible. Callers must opt in by wrapping their
    * string with [[TrustedHtml.unsafe]], signalling that the content is either
    * static or has already been sanitised.
    *
    * {{{
    * // OK — static, developer-controlled markup
    * Bind.html(el, TrustedHtml.unsafe("<strong>Hello</strong>"))
    *
    * // OK — sanitised reactive content
    * val safeHtml: Var[TrustedHtml] = content.map(s => TrustedHtml.unsafe(sanitise(s)))
    * Bind.html(el, safeHtml)
    *
    * // Compile error — plain Var[String] no longer accepted
    * Bind.html(el, Var("<b>user input</b>"))
    * }}}
    */
  def html(el: dom.Element, content: Var[TrustedHtml]): Unit =
    el.innerHTML = content.now().value
    val cancel = content.subscribe(s => el.innerHTML = s.value)
    Cleanup.register(cancel)

  def html(el: dom.Element, content: Signal[TrustedHtml]): Unit =
    el.innerHTML = content.now().value
    val cancel = content.subscribe(s => el.innerHTML = s.value)
    Cleanup.register(cancel)

  def html(el: dom.Element, content: TrustedHtml): Unit =
    el.innerHTML = content.value

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

  // ── Dynamic element (<melt:element this={tag}>) ───────────────────────

  private val htmlCreateElement: String => dom.Element = dom.document.createElement
  private val svgCreateElement:  String => dom.Element = dom.document.createElementNS(SvgTag.namespace, _)
  private val mathCreateElement: String => dom.Element = dom.document.createElementNS(MathTag.namespace, _)

  /** Renders a single HTML element whose tag name is determined at call time.
    * `null` / `None` renders nothing (anchor comment remains in the DOM).
    * Supports `transition:`, `in:`, and `out:` directives on the element.
    */
  @scala.annotation.targetName("dynamicElementHtml")
  def dynamicElement(
    tag:     HtmlTag | Null,
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag, anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementHtmlOption")
  def dynamicElement(
    tag:     Option[HtmlTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag.orNull, anchor, scopeId, setup, htmlCreateElement)

  /** Reactive HTML dynamic element — re-renders whenever `tag` changes.
    * The old element plays its outro (if any) then is removed; the new element is inserted
    * and its intro (if any) is played.  `null` / `None` renders nothing.
    */
  @scala.annotation.targetName("dynamicElementSignal")
  def dynamicElement(
    tag:     Signal[HtmlTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), f => tag.subscribe(v => f(v)), anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementSignalNullable")
  def dynamicElement(
    tag:     Signal[HtmlTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), tag.subscribe, anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementSignalOption")
  def dynamicElement(
    tag:     Signal[Option[HtmlTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    tag.now().orNull,
    f => tag.subscribe(opt => f(opt.orNull)),
    anchor,
    scopeId,
    setup,
    htmlCreateElement
  )

  @scala.annotation.targetName("dynamicElementVar")
  def dynamicElement(
    tag:     Var[HtmlTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), f => tag.subscribe(v => f(v)), anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementVarNullable")
  def dynamicElement(
    tag:     Var[HtmlTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), tag.subscribe, anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementVarOption")
  def dynamicElement(
    tag:     Var[Option[HtmlTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    tag.now().orNull,
    f => tag.subscribe(opt => f(opt.orNull)),
    anchor,
    scopeId,
    setup,
    htmlCreateElement
  )

  // ── SVG overloads ────────────────────────────────────────────────────────

  /** Renders a single SVG element (via `createElementNS`) whose tag name is
    * determined at call time. Use inside an `<svg>` context.
    */
  @scala.annotation.targetName("dynamicElementSvg")
  def dynamicElement(
    tag:     SvgTag | Null,
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag, anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgOption")
  def dynamicElement(
    tag:     Option[SvgTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag.orNull, anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgSignal")
  def dynamicElement(
    tag:     Signal[SvgTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), f => tag.subscribe(v => f(v)), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgSignalNullable")
  def dynamicElement(
    tag:     Signal[SvgTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), tag.subscribe, anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgSignalOption")
  def dynamicElement(
    tag:     Signal[Option[SvgTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    tag.now().orNull,
    f => tag.subscribe(opt => f(opt.orNull)),
    anchor,
    scopeId,
    setup,
    svgCreateElement
  )

  @scala.annotation.targetName("dynamicElementSvgVar")
  def dynamicElement(
    tag:     Var[SvgTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), f => tag.subscribe(v => f(v)), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgVarNullable")
  def dynamicElement(
    tag:     Var[SvgTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), tag.subscribe, anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgVarOption")
  def dynamicElement(
    tag:     Var[Option[SvgTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    tag.now().orNull,
    f => tag.subscribe(opt => f(opt.orNull)),
    anchor,
    scopeId,
    setup,
    svgCreateElement
  )

  // ── MathML overloads ────────────────────────────────────────────────────

  /** Renders a single MathML element (via `createElementNS`) whose tag name is
    * determined at call time. Use inside a `<math>` context.
    */
  @scala.annotation.targetName("dynamicElementMath")
  def dynamicElement(
    tag:     MathTag | Null,
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag, anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathOption")
  def dynamicElement(
    tag:     Option[MathTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag.orNull, anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathSignal")
  def dynamicElement(
    tag:     Signal[MathTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), f => tag.subscribe(v => f(v)), anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathSignalNullable")
  def dynamicElement(
    tag:     Signal[MathTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), tag.subscribe, anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathSignalOption")
  def dynamicElement(
    tag:     Signal[Option[MathTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    tag.now().orNull,
    f => tag.subscribe(opt => f(opt.orNull)),
    anchor,
    scopeId,
    setup,
    mathCreateElement
  )

  @scala.annotation.targetName("dynamicElementMathVar")
  def dynamicElement(
    tag:     Var[MathTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), f => tag.subscribe(v => f(v)), anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathVarNullable")
  def dynamicElement(
    tag:     Var[MathTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.now(), tag.subscribe, anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathVarOption")
  def dynamicElement(
    tag:     Var[Option[MathTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    tag.now().orNull,
    f => tag.subscribe(opt => f(opt.orNull)),
    anchor,
    scopeId,
    setup,
    mathCreateElement
  )

  // ── Private helpers ──────────────────────────────────────────────────────

  /** Creates a single element, runs setup, plays intro transition if present, then
    * registers cleanup that plays the outro (if present) before removing the element.
    */
  private def mountDynamicOnce(
    tagName:       String | Null,
    anchor:        dom.Comment,
    scopeId:       String,
    setup:         dom.Element => Unit,
    createElement: String => dom.Element
  ): Unit =
    if tagName != null then
      val el = createElement(tagName)
      el.classList.add(scopeId)
      setup(el)
      anchor.parentNode.insertBefore(el, anchor)
      if TransitionBridge.hasIn(el) then TransitionBridge.playIn(el)
      else playGlobalTransitions(el, intro = true)
      Cleanup.register(() =>
        if TransitionBridge.hasOut(el) then
          playGlobalTransitions(el, intro = false)
          TransitionBridge.playOut(el, () => Option(anchor.parentNode).foreach(_.removeChild(el)))
        else
          playGlobalTransitions(el, intro = false)
          Option(anchor.parentNode).foreach(_.removeChild(el))
      )

  /** Reactive helper: re-creates the element whenever `subscribeFn` fires.
    * Plays intro/outro transitions on enter/leave.
    * Each element is scoped by its own [[OwnerNode]] so that reactive subscriptions
    * created during `setup(el)` are destroyed automatically when the element is swapped.
    */
  private def mountDynamicCore(
    initial:       String | Null,
    subscribeFn:   ((String | Null) => Unit) => (() => Unit),
    anchor:        dom.Comment,
    scopeId:       String,
    setup:         dom.Element => Unit,
    createElement: String => dom.Element
  ): Unit =
    var current: dom.Element | Null = null
    // Holds the OwnerNode for subscriptions created by setup() for the *current* element.
    // Destroyed before each swap so reactive bindings don't fire on detached nodes.
    var elementNode: Option[OwnerNode] = None

    def swap(tagName: String | Null): Unit =
      // Destroy all subscriptions registered by the previous setup call
      elementNode.foreach(_.destroy())
      elementNode = None
      if current != null then
        val old = current
        current = null
        if TransitionBridge.hasOut(old) then
          playGlobalTransitions(old, intro = false)
          TransitionBridge.playOut(old, () => Option(old.parentNode).foreach(_.removeChild(old)))
        else
          playGlobalTransitions(old, intro = false)
          Option(anchor.parentNode).foreach(_.removeChild(old))
      if tagName != null then
        val el = createElement(tagName)
        el.classList.add(scopeId)
        // Scope setup's Cleanup.register calls to a per-element OwnerNode
        val (_, node) = Owner.withNew { setup(el) }
        elementNode = Some(node)
        anchor.parentNode.insertBefore(el, anchor)
        if TransitionBridge.hasIn(el) then
          TransitionBridge.playIn(el)
          playGlobalTransitions(el, intro = true)
        else playGlobalTransitions(el, intro = true)
        current = el

    swap(initial)
    val cancel = subscribeFn(swap)
    Cleanup.register(cancel)
    Cleanup.register(() => {
      elementNode.foreach(_.destroy())
      if current != null then
        val old = current
        current = null
        if TransitionBridge.hasOut(old) then
          playGlobalTransitions(old, intro = false)
          TransitionBridge.playOut(old, () => Option(old.parentNode).foreach(_.removeChild(old)))
        else
          playGlobalTransitions(old, intro = false)
          Option(anchor.parentNode).foreach(_.removeChild(old))
    })
