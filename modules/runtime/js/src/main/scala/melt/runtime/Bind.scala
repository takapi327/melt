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
    val node = dom.document.createTextNode(v.value.toString)
    parent.appendChild(node)
    val cancel = v.subscribe(a => node.textContent = a.toString)
    Cleanup.register(cancel)
    node

  def text(signal: Signal[?], parent: dom.Node): dom.Text =
    val node = dom.document.createTextNode(signal.value.toString)
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
    el.setAttribute(name, v.value.toString)
    val cancel = v.subscribe(a => el.setAttribute(name, a.toString))
    Cleanup.register(cancel)

  def attr(el: dom.Element, name: String, signal: Signal[?]): Unit =
    el.setAttribute(name, signal.value.toString)
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

  // ── Class attribute ────────────────────────────────────────────────────

  /** Reactive `class` binding for `Var[String]`. Tracks previously-added
    * classes so scoped IDs and other classes set outside this binding are
    * preserved on each update.
    */
  def cls(el: dom.Element, v: Var[String]): Unit =
    cls(el, v.signal)

  /** Reactive `class` binding for `Signal[String]`. */
  def cls(el: dom.Element, signal: Signal[String]): Unit =
    var current = signal.value.split("\\s+").filter(_.nonEmpty).toSet
    current.foreach(el.classList.add(_))
    val cancel = signal.subscribe { s =>
      val next = s.split("\\s+").filter(_.nonEmpty).toSet
      (current -- next).foreach(el.classList.remove(_))
      (next -- current).foreach(el.classList.add(_))
      current = next
    }
    Cleanup.register(cancel)

  /** Static `class` binding. */
  def cls(el: dom.Element, value: String): Unit =
    value.split("\\s+").filter(_.nonEmpty).foreach(el.classList.add(_))

  // ── Optional attribute ─────────────────────────────────────────────────

  def optionalAttr[A](el: dom.Element, name: String, v: Var[Option[A]]): Unit =
    optionalAttr(el, name, v.signal)

  def optionalAttr[A](el: dom.Element, name: String, signal: Signal[Option[A]]): Unit =
    def apply(v: Option[A]): Unit = v match
      case Some(a) => el.setAttribute(name, a.toString)
      case None    => el.removeAttribute(name)
    apply(signal.value)
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  // ── Boolean attribute ──────────────────────────────────────────────────

  def booleanAttr(el: dom.Element, name: String, v: Var[Boolean]): Unit =
    booleanAttr(el, name, v.signal)

  def booleanAttr(el: dom.Element, name: String, signal: Signal[Boolean]): Unit =
    def apply(b: Boolean): Unit =
      if b then el.setAttribute(name, "") else el.removeAttribute(name)
    apply(signal.value)
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  def booleanAttr(el: dom.Element, name: String, value: Boolean): Unit =
    if value then el.setAttribute(name, "") else el.removeAttribute(name)
    el.asInstanceOf[scalajs.js.Dynamic].updateDynamic(name)(value)

  // ── Two-way bindings ───────────────────────────────────────────────────

  /** Two-way string binding for `<input>`. */
  def inputValue(input: dom.html.Input, v: Var[String]): Unit =
    input.value = v.value
    val cancelSub = v.subscribe(s => if input.value != s then input.value = s)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => v.set(input.value)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))

  /** Two-way Int binding for `<input type="number">`. */
  def inputInt(input: dom.html.Input, v: Var[Int]): Unit =
    input.value = v.value.toString
    val cancelSub = v.subscribe(n => { val s = n.toString; if input.value != s then input.value = s })
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => input.value.toIntOption.foreach(v.set)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))

  /** Two-way Double binding for `<input type="number">`. */
  def inputDouble(input: dom.html.Input, v: Var[Double]): Unit =
    input.value = v.value.toString
    val cancelSub = v.subscribe(n => { val s = n.toString; if input.value != s then input.value = s })
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => input.value.toDoubleOption.foreach(v.set)
    input.addEventListener("input", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("input", listener))

  /** Two-way checkbox binding. */
  def inputChecked(input: dom.html.Input, v: Var[Boolean]): Unit =
    input.checked = v.value
    val cancelSub = v.subscribe(b => if input.checked != b then input.checked = b)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => v.set(input.checked)
    input.addEventListener("change", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("change", listener))

  /** Two-way radio group binding. */
  def radioGroup(input: dom.html.Input, v: Var[String], value: String): Unit =
    input.checked = v.value == value
    val cancelSub = v.subscribe(s => input.checked = s == value)
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => if input.checked then v.set(value)
    input.addEventListener("change", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("change", listener))

  /** Two-way checkbox group binding (list of selected values). */
  def checkboxGroup(input: dom.html.Input, v: Var[List[String]], value: String): Unit =
    input.checked = v.value.contains(value)
    val cancelSub = v.subscribe(list => input.checked = list.contains(value))
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) =>
      if input.checked then { if !v.value.contains(value) then v.update(_ :+ value) }
      else v.update(_.filterNot(_ == value))
    input.addEventListener("change", listener)
    Cleanup.register(cancelSub)
    Cleanup.register(() => input.removeEventListener("change", listener))

  /** Two-way string binding for `<textarea>`. */
  def textareaValue(textarea: dom.html.TextArea, v: Var[String]): Unit =
    var lastVarValue = v.value
    textarea.value = lastVarValue
    val cancelSub = v.subscribe { s =>
      if lastVarValue != s then
        lastVarValue   = s
        textarea.value = s
    }
    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) => v.set(textarea.value)
    textarea.addEventListener("input", listener)
    val resetHandler = () =>
      val dv = textarea.defaultValue
      textarea.value = dv
      v.set(dv)
    FormReset.register(textarea, resetHandler)
    Cleanup.register(cancelSub)
    Cleanup.register(() => textarea.removeEventListener("input", listener))
    Cleanup.register(() => FormReset.unregister(textarea))

  /** Two-way string binding for `<select>` (single selection).
    *
    * Must be called *after* `<option>` children are appended to the DOM so that
    * the initial `select.value` assignment can find a matching option.
    */
  def selectValue(select: dom.html.Select, v: Var[String]): Unit =
    def options: IndexedSeq[dom.html.Option] =
      (0 until select.options.length).map(i => select.options(i).asInstanceOf[dom.html.Option])

    def applyValue(s: String): Unit =
      select.value                                   = s
      if select.value != s then select.selectedIndex = -1

    applyValue(v.value)

    val cancelSub = v.subscribe(s => if select.value != s then applyValue(s))

    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) =>
      Option(select.querySelector(":checked"))
        .foreach(el => v.set(el.asInstanceOf[dom.html.Option].value))
    select.addEventListener("change", listener)

    val observer = new dom.MutationObserver((_, _) => applyValue(v.value))
    observer.observe(
      select,
      scalajs.js.Dynamic
        .literal(childList = true, subtree = true, attributes = true, attributeFilter = scalajs.js.Array("value"))
        .asInstanceOf[dom.MutationObserverInit]
    )

    val resetHandler = () =>
      val defaultVal = options.find(_.defaultSelected).map(_.value).getOrElse("")
      applyValue(defaultVal)
      v.set(defaultVal)
    FormReset.register(select, resetHandler)

    Cleanup.register(cancelSub)
    Cleanup.register(() => select.removeEventListener("change", listener))
    Cleanup.register(() => observer.disconnect())
    Cleanup.register(() => FormReset.unregister(select))

  /** Two-way List[String] binding for `<select multiple>`.
    *
    * Must be called *after* `<option>` children are appended to the DOM.
    */
  def selectMultipleValue(select: dom.html.Select, v: Var[List[String]]): Unit =
    def options: IndexedSeq[dom.html.Option] =
      (0 until select.options.length).map(i => select.options(i).asInstanceOf[dom.html.Option])

    def applyValue(vals: List[String]): Unit =
      options.foreach(opt => opt.selected = vals.contains(opt.value))

    applyValue(v.value)

    val cancelSub = v.subscribe(applyValue)

    val listener: scalajs.js.Function1[dom.Event, Unit] = (_: dom.Event) =>
      val checked = select.querySelectorAll(":checked")
      v.set(
        (0 until checked.length)
          .map(i => checked(i).asInstanceOf[dom.html.Option].value)
          .toList
      )
    select.addEventListener("change", listener)

    val observer = new dom.MutationObserver((_, _) => applyValue(v.value))
    observer.observe(
      select,
      scalajs.js.Dynamic
        .literal(childList = true, subtree = true, attributes = true, attributeFilter = scalajs.js.Array("value"))
        .asInstanceOf[dom.MutationObserverInit]
    )

    val resetHandler = () =>
      val defaults = options.filter(_.defaultSelected).map(_.value).toList
      applyValue(defaults)
      v.set(defaults)
    FormReset.register(select, resetHandler)

    Cleanup.register(cancelSub)
    Cleanup.register(() => select.removeEventListener("change", listener))
    Cleanup.register(() => observer.disconnect())
    Cleanup.register(() => FormReset.unregister(select))

  // ── Class toggle (class:name={expr}) ───────────────────────────────────

  def classToggle(el: dom.Element, className: String, v: Var[Boolean]): Unit =
    classToggle(el, className, v.signal)

  def classToggle(el: dom.Element, className: String, signal: Signal[Boolean]): Unit =
    def apply(b: Boolean): Unit =
      if b then el.classList.add(className) else el.classList.remove(className)
    apply(signal.value)
    val cancel = signal.subscribe(apply)
    Cleanup.register(cancel)

  def classToggle(el: dom.Element, className: String, value: Boolean): Unit =
    if value then el.classList.add(className) else el.classList.remove(className)

  // ── Style binding (style:property={expr}) ──────────────────────────────

  def style(el: dom.Element, property: String, v: Var[?]): Unit =
    el.asInstanceOf[dom.html.Element].style.setProperty(property, v.value.toString)
    val cancel = v.subscribe(a => el.asInstanceOf[dom.html.Element].style.setProperty(property, a.toString))
    Cleanup.register(cancel)

  def style(el: dom.Element, property: String, signal: Signal[?]): Unit =
    el.asInstanceOf[dom.html.Element].style.setProperty(property, signal.value.toString)
    val cancel =
      signal.subscribe(a => el.asInstanceOf[dom.html.Element].style.setProperty(property, a.toString))
    Cleanup.register(cancel)

  def style(el: dom.Element, property: String, value: Any): Unit =
    el.asInstanceOf[dom.html.Element].style.setProperty(property, value.toString)

  // ── Element dimension bindings (bind:clientWidth etc.) ─────────────────

  /** One-way binding: `element.clientWidth → Var[Double]` (read-only).
    *
    * Updates the Var whenever the element is resized via `ResizeObserver`.
    * Corresponds to `bind:clientWidth` in Svelte 5.
    */
  def clientWidth(el: dom.Element, v: Var[Double]): Unit =
    v.set(el.clientWidth.toDouble)
    val observer = new dom.ResizeObserver((_, _) => v.set(el.clientWidth.toDouble))
    observer.observe(el)
    Cleanup.register(() => observer.disconnect())

  /** One-way binding: `element.clientHeight → Var[Double]` (read-only).
    *
    * Updates the Var whenever the element is resized via `ResizeObserver`.
    * Corresponds to `bind:clientHeight` in Svelte 5.
    */
  def clientHeight(el: dom.Element, v: Var[Double]): Unit =
    v.set(el.clientHeight.toDouble)
    val observer = new dom.ResizeObserver((_, _) => v.set(el.clientHeight.toDouble))
    observer.observe(el)
    Cleanup.register(() => observer.disconnect())

  /** One-way binding: `element.offsetWidth → Var[Double]` (read-only).
    *
    * Updates the Var whenever the element is resized via `ResizeObserver`.
    * Corresponds to `bind:offsetWidth` in Svelte 5.
    */
  def offsetWidth(el: dom.Element, v: Var[Double]): Unit =
    v.set(el.asInstanceOf[dom.html.Element].offsetWidth.toDouble)
    val observer =
      new dom.ResizeObserver((_, _) => v.set(el.asInstanceOf[dom.html.Element].offsetWidth.toDouble))
    observer.observe(el)
    Cleanup.register(() => observer.disconnect())

  /** One-way binding: `element.offsetHeight → Var[Double]` (read-only).
    *
    * Updates the Var whenever the element is resized via `ResizeObserver`.
    * Corresponds to `bind:offsetHeight` in Svelte 5.
    */
  def offsetHeight(el: dom.Element, v: Var[Double]): Unit =
    v.set(el.asInstanceOf[dom.html.Element].offsetHeight.toDouble)
    val observer =
      new dom.ResizeObserver((_, _) => v.set(el.asInstanceOf[dom.html.Element].offsetHeight.toDouble))
    observer.observe(el)
    Cleanup.register(() => observer.disconnect())

  // ── Media element bindings (bind:currentTime, bind:paused etc.) ───────

  /** Two-way binding: `media.currentTime ↔ Var[Double]`.
    *
    * - DOM → Var: updated on every `timeupdate` event.
    * - Var → DOM: seeks to the new position, skipping the initial value
    *   to avoid an unexpected seek-to-zero on mount.
    *
    * Corresponds to `bind:currentTime` in Svelte 5.
    */
  def mediaCurrentTime(el: dom.Element, v: Var[Double]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    v.set(media.currentTime)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ =>
      if v.value != media.currentTime then v.set(media.currentTime)
    media.addEventListener("timeupdate", listener)
    Cleanup.register(() => media.removeEventListener("timeupdate", listener))
    val cancel = v.signal.subscribe { t =>
      if t != media.currentTime then media.currentTime = t
    }
    Cleanup.register(cancel)

  /** One-way binding: `media.duration → Var[Double]` (read-only).
    *
    * Updated on `durationchange` and `loadedmetadata`.
    * Corresponds to `bind:duration` in Svelte 5.
    */
  def mediaDuration(el: dom.Element, v: Var[Double]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(media.duration)
    media.addEventListener("durationchange", listener)
    media.addEventListener("loadedmetadata", listener)
    Cleanup.register(() => {
      media.removeEventListener("durationchange", listener)
      media.removeEventListener("loadedmetadata", listener)
    })

  /** Two-way binding: `media.paused ↔ Var[Boolean]`.
    *
    * - DOM → Var: updated on `play` and `pause` events.
    * - Var → DOM: calls `media.play()` or `media.pause()`, skipping the
    *   initial value to avoid auto-playing on mount.
    *
    * Corresponds to `bind:paused` in Svelte 5.
    */
  def mediaPaused(el: dom.Element, v: Var[Boolean]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    v.set(media.paused)
    // DOM → Var: only update if value changed to avoid feedback loops
    val playListener:  scalajs.js.Function1[dom.Event, Unit] = _ => if v.value then v.set(false)
    val pauseListener: scalajs.js.Function1[dom.Event, Unit] = _ => if !v.value then v.set(true)
    media.addEventListener("play", playListener)
    media.addEventListener("pause", pauseListener)
    Cleanup.register(() => {
      media.removeEventListener("play", playListener)
      media.removeEventListener("pause", pauseListener)
    })
    // Var → DOM: Signal.subscribe is lazy so no initialized guard needed;
    // equality check prevents feedback loops
    val cancel = v.signal.subscribe { paused =>
      if paused != media.paused then
        if paused then media.pause()
        else { val _ = media.play() }
    }
    Cleanup.register(cancel)

  /** Two-way binding: `media.volume ↔ Var[Double]` (0.0–1.0).
    *
    * - DOM → Var: updated on `volumechange`.
    * - Var → DOM: sets `media.volume`, skipping the initial value.
    *
    * Corresponds to `bind:volume` in Svelte 5.
    */
  def mediaVolume(el: dom.Element, v: Var[Double]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    v.set(media.volume)
    // DOM → Var: only update if value actually changed to avoid feedback loops
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ =>
      if v.value != media.volume then v.set(media.volume)
    media.addEventListener("volumechange", listener)
    Cleanup.register(() => media.removeEventListener("volumechange", listener))
    val cancel = v.signal.subscribe { vol =>
      if vol != media.volume then media.volume = vol
    }
    Cleanup.register(cancel)

  /** Two-way binding: `media.muted ↔ Var[Boolean]`.
    *
    * - DOM → Var: updated on `volumechange`.
    * - Var → DOM: sets `media.muted`, skipping the initial value.
    *
    * Corresponds to `bind:muted` in Svelte 5.
    */
  def mediaMuted(el: dom.Element, v: Var[Boolean]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    v.set(media.muted)
    // DOM → Var: only update if value actually changed to avoid feedback loops
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ =>
      if v.value != media.muted then v.set(media.muted)
    media.addEventListener("volumechange", listener)
    Cleanup.register(() => media.removeEventListener("volumechange", listener))
    val cancel = v.signal.subscribe { muted =>
      if muted != media.muted then media.muted = muted
    }
    Cleanup.register(cancel)

  /** Two-way binding: `media.playbackRate ↔ Var[Double]`.
    *
    * - DOM → Var: updated on `ratechange`.
    * - Var → DOM: sets `media.playbackRate`, skipping the initial value.
    *
    * Corresponds to `bind:playbackRate` in Svelte 5.
    */
  def mediaPlaybackRate(el: dom.Element, v: Var[Double]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    v.set(media.playbackRate)
    // DOM → Var: only update if value actually changed to avoid feedback loops
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ =>
      if v.value != media.playbackRate then v.set(media.playbackRate)
    media.addEventListener("ratechange", listener)
    Cleanup.register(() => media.removeEventListener("ratechange", listener))
    val cancel = v.signal.subscribe { rate =>
      if rate != media.playbackRate then media.playbackRate = rate
    }
    Cleanup.register(cancel)

  /** One-way binding: `media.seeking → Var[Boolean]` (read-only).
    *
    * `true` while the user is seeking; `false` once seeking completes.
    * Corresponds to `bind:seeking` in Svelte 5.
    */
  def mediaSeeking(el: dom.Element, v: Var[Boolean]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    val seekingListener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(true)
    val seekedListener:  scalajs.js.Function1[dom.Event, Unit] = _ => v.set(false)
    media.addEventListener("seeking", seekingListener)
    media.addEventListener("seeked", seekedListener)
    Cleanup.register(() => {
      media.removeEventListener("seeking", seekingListener)
      media.removeEventListener("seeked", seekedListener)
    })

  /** One-way binding: `media.ended → Var[Boolean]` (read-only).
    *
    * `true` when playback reaches the end; resets to `false` on `play`.
    * Corresponds to `bind:ended` in Svelte 5.
    */
  def mediaEnded(el: dom.Element, v: Var[Boolean]): Unit =
    val media = el.asInstanceOf[dom.html.Media]
    val endedListener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(true)
    val playListener:  scalajs.js.Function1[dom.Event, Unit] = _ => v.set(false)
    media.addEventListener("ended", endedListener)
    media.addEventListener("play", playListener)
    Cleanup.register(() => {
      media.removeEventListener("ended", endedListener)
      media.removeEventListener("play", playListener)
    })

  /** One-way binding: `media.readyState → Var[Int]` (read-only, 0–4).
    *
    * 0=HAVE_NOTHING, 1=HAVE_METADATA, 2=HAVE_CURRENT_DATA,
    * 3=HAVE_FUTURE_DATA, 4=HAVE_ENOUGH_DATA.
    * Corresponds to `bind:readyState` in Svelte 5.
    */
  def mediaReadyState(el: dom.Element, v: Var[Int]): Unit =
    val media  = el.asInstanceOf[dom.html.Media]
    val events = List("emptied", "loadedmetadata", "loadeddata", "canplay", "canplaythrough")
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(media.readyState.asInstanceOf[Int])
    events.foreach(media.addEventListener(_, listener))
    Cleanup.register(() => events.foreach(media.removeEventListener(_, listener)))

  /** One-way binding: `video.videoWidth → Var[Int]` (read-only, `<video>` only).
    *
    * Intrinsic width of the video in CSS pixels. Updated on `loadedmetadata`.
    * Corresponds to `bind:videoWidth` in Svelte 5.
    */
  def mediaVideoWidth(el: dom.Element, v: Var[Int]): Unit =
    val video = el.asInstanceOf[dom.html.Video]
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(video.videoWidth)
    video.addEventListener("loadedmetadata", listener)
    Cleanup.register(() => video.removeEventListener("loadedmetadata", listener))

  /** One-way binding: `video.videoHeight → Var[Int]` (read-only, `<video>` only).
    *
    * Intrinsic height of the video in CSS pixels. Updated on `loadedmetadata`.
    * Corresponds to `bind:videoHeight` in Svelte 5.
    */
  def mediaVideoHeight(el: dom.Element, v: Var[Int]): Unit =
    val video = el.asInstanceOf[dom.html.Video]
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(video.videoHeight)
    video.addEventListener("loadedmetadata", listener)
    Cleanup.register(() => video.removeEventListener("loadedmetadata", listener))

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
    var current: dom.Node = render(v.value)
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
    var current: dom.Node = render(signal.value)
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

  // ── Key-block rendering (<melt:key this={expr}>) ─────────────────────

  /** Reactive key-block rendering for [[Var]].
    *
    * Destroys and re-creates the content block whenever the key value **changes**
    * (equality-guarded — prevents remounting on same-value writes).
    *
    * Uses a dual-anchor pattern (`startAnchor` / `endAnchor` comment nodes) so
    * no wrapper element appears in the DOM.  The render lambda returns a
    * [[dom.DocumentFragment]]; its direct children are inserted between the two
    * anchors, and each child [[dom.Element]] has its `in:` / `out:` transition
    * played individually (Svelte 5 parity).
    *
    * The old reactive scope ([[OwnerNode]]) is kept alive until all outro
    * animations complete so the leaving content stays reactive during CSS transitions.
    */
  def key(
    source:      Var[?],
    render:      () => dom.DocumentFragment,
    startAnchor: dom.Comment,
    endAnchor:   dom.Comment
  ): Unit =
    var currentNodes:   List[dom.Node]    = Nil
    var elementNode:    Option[OwnerNode] = None
    var lastKey:        Option[Any]       = None
    var animatingNodes: Set[dom.Element]  = Set.empty

    def nodesBetween(): List[dom.Node] =
      val buf  = mutable.ListBuffer.empty[dom.Node]
      var node = startAnchor.nextSibling
      while node != null && (node ne endAnchor) do
        buf += node
        node = node.nextSibling
      buf.toList

    def removeNodes(nodes: List[dom.Node], owner: OwnerNode): Unit =
      val elements = nodes.collect { case el: dom.Element => el }
      val withOut  = elements.filter(TransitionBridge.hasOut)
      elements.foreach(playGlobalTransitions(_, intro = false))
      if withOut.isEmpty then
        owner.destroy()
        elements.foreach(Lifecycle.destroyTree)
        nodes.foreach { n => Option(n.parentNode).foreach(_.removeChild(n)) }
      else
        val noAnim = nodes.filterNot(n => withOut.contains(n))
        elements.filterNot(withOut.contains).foreach(Lifecycle.destroyTree)
        noAnim.foreach { n => Option(n.parentNode).foreach(_.removeChild(n)) }
        var remaining = withOut.size
        withOut.foreach { el =>
          animatingNodes += el
          Lifecycle.destroyTree(el)
          TransitionBridge.playOut(
            el,
            () => {
              animatingNodes -= el
              Option(el.parentNode).foreach(_.removeChild(el))
              remaining -= 1
              if remaining == 0 then owner.destroy()
            }
          )
        }

    def mount(): Unit =
      lastKey = Some(source.value)
      // Cancel in-progress outros on rapid key change.
      animatingNodes.foreach { el => Option(el.parentNode).foreach(_.removeChild(el)) }
      animatingNodes = Set.empty

      val prevNodes = nodesBetween()
      val prevOwner = elementNode
      elementNode  = None
      currentNodes = Nil

      val (frag, node) = Owner.withNew { render() }
      elementNode = Some(node)

      // Snapshot direct children BEFORE insertBefore (they move out of the fragment).
      val directChildren = (0 until frag.childNodes.length)
        .map(i => frag.childNodes(i))
        .toList

      Option(endAnchor.parentNode).foreach(_.insertBefore(frag, endAnchor))
      currentNodes = directChildren

      directChildren.foreach {
        case el: dom.Element if TransitionBridge.hasIn(el) =>
          TransitionBridge.playIn(el)
          playGlobalTransitions(el, intro = true)
        case el: dom.Element =>
          playGlobalTransitions(el, intro = true)
        case _ =>
      }

      prevOwner.foreach(removeNodes(prevNodes, _))

    mount()
    val cancel = source.subscribe { newVal =>
      if !lastKey.contains(newVal) then mount()
    }
    Cleanup.register(cancel)
    Cleanup.register(() => {
      val prevNodes = nodesBetween()
      elementNode.foreach(removeNodes(prevNodes, _))
      currentNodes = Nil
      elementNode  = None
    })

  /** Reactive key-block rendering for [[Signal]].
    *
    * Identical to the [[Var]] overload but subscribes to a [[Signal]] source.
    */
  def key(
    source:      Signal[?],
    render:      () => dom.DocumentFragment,
    startAnchor: dom.Comment,
    endAnchor:   dom.Comment
  ): Unit =
    var currentNodes:   List[dom.Node]    = Nil
    var elementNode:    Option[OwnerNode] = None
    var lastKey:        Option[Any]       = None
    var animatingNodes: Set[dom.Element]  = Set.empty

    def nodesBetween(): List[dom.Node] =
      val buf  = mutable.ListBuffer.empty[dom.Node]
      var node = startAnchor.nextSibling
      while node != null && (node ne endAnchor) do
        buf += node
        node = node.nextSibling
      buf.toList

    def removeNodes(nodes: List[dom.Node], owner: OwnerNode): Unit =
      val elements = nodes.collect { case el: dom.Element => el }
      val withOut  = elements.filter(TransitionBridge.hasOut)
      elements.foreach(playGlobalTransitions(_, intro = false))
      if withOut.isEmpty then
        owner.destroy()
        elements.foreach(Lifecycle.destroyTree)
        nodes.foreach { n => Option(n.parentNode).foreach(_.removeChild(n)) }
      else
        val noAnim = nodes.filterNot(n => withOut.contains(n))
        elements.filterNot(withOut.contains).foreach(Lifecycle.destroyTree)
        noAnim.foreach { n => Option(n.parentNode).foreach(_.removeChild(n)) }
        var remaining = withOut.size
        withOut.foreach { el =>
          animatingNodes += el
          Lifecycle.destroyTree(el)
          TransitionBridge.playOut(
            el,
            () => {
              animatingNodes -= el
              Option(el.parentNode).foreach(_.removeChild(el))
              remaining -= 1
              if remaining == 0 then owner.destroy()
            }
          )
        }

    def mount(): Unit =
      lastKey = Some(source.value)
      animatingNodes.foreach { el => Option(el.parentNode).foreach(_.removeChild(el)) }
      animatingNodes = Set.empty

      val prevNodes = nodesBetween()
      val prevOwner = elementNode
      elementNode  = None
      currentNodes = Nil

      val (frag, node) = Owner.withNew { render() }
      elementNode = Some(node)

      val directChildren = (0 until frag.childNodes.length)
        .map(i => frag.childNodes(i))
        .toList

      Option(endAnchor.parentNode).foreach(_.insertBefore(frag, endAnchor))
      currentNodes = directChildren

      directChildren.foreach {
        case el: dom.Element if TransitionBridge.hasIn(el) =>
          TransitionBridge.playIn(el)
          playGlobalTransitions(el, intro = true)
        case el: dom.Element =>
          playGlobalTransitions(el, intro = true)
        case _ =>
      }

      prevOwner.foreach(removeNodes(prevNodes, _))

    mount()
    val cancel = source.subscribe { newVal =>
      if !lastKey.contains(newVal) then mount()
    }
    Cleanup.register(cancel)
    Cleanup.register(() => {
      val prevNodes = nodesBetween()
      elementNode.foreach(removeNodes(prevNodes, _))
      currentNodes = Nil
      elementNode  = None
    })

  /** Compile-time guard: the `this={...}` expression in `<melt:key>` must be
    * a [[Var]][?] or [[Signal]][?].
    *
    * Fires when the key expression resolves to any other type (e.g. a plain
    * `Int` field access like `user.id` where `user: Var[User]`).  Use `.map()`
    * to derive a [[Signal]] from the reactive container instead.
    */
  @scala.annotation.targetName("keyInvalidSource")
  inline def key(source: Any, render: Any, startAnchor: Any, endAnchor: Any): Unit =
    scala.compiletime.error(
      "The `this={...}` expression in <melt:key> must be a Var[?] or Signal[?].\n" +
        "If your key is a field of a reactive object, use .map() to derive a Signal:\n" +
        "  Instead of:  <melt:key this={user.id}>\n" +
        "  Write:       <melt:key this={user.map(_.id)}>"
    )

  // ── List rendering ────────────────────────────────────────────────────

  /** Renders a plain (non-reactive) collection before `anchor`. Used
    * when a `.melt` template references `{items.map(renderFn)}` where
    * `items` is a plain `List` / `Seq` / `Iterable`, not a `Var` /
    * `Signal`.
    *
    * This is a one-shot render: subsequent changes to `items` (if any)
    * are not reflected. For reactive list rendering use `Var[List[A]]`
    * and the overload below.
    */
  def list[A](source: Iterable[A], renderFn: A => dom.Node, anchor: dom.Node): Unit =
    val parent = anchor.parentNode
    source.foreach { item =>
      val node = renderFn(item)
      parent.insertBefore(node, anchor)
    }

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

    rebuild(source.value)
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

    rebuild(source.value)
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

    rebuild(source.value)
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

    rebuild(source.value)
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
    el.innerHTML = content.value.value
    val cancel = content.subscribe(s => el.innerHTML = s.value)
    Cleanup.register(cancel)

  def html(el: dom.Element, content: Signal[TrustedHtml]): Unit =
    el.innerHTML = content.value.value
    val cancel = content.subscribe(s => el.innerHTML = s.value)
    Cleanup.register(cancel)

  def html(el: dom.Element, content: TrustedHtml): Unit =
    el.innerHTML = content.value

  // ── Action binding (use: directive) ───────────────────────────────────

  /** Applies an action to an element with a static parameter. */
  def action[P](el: dom.Element, act: Action[P], param: P): Unit =
    val wrapped = melt.runtime.dom.Conversions.wrapElement(el)
    val cleanup = act(wrapped, param)
    Cleanup.register(cleanup)

  /** Applies an action with a reactive Var parameter. Re-applies on change. */
  def action[P](el: dom.Element, act: Action[P], param: Var[P]): Unit =
    val wrapped = melt.runtime.dom.Conversions.wrapElement(el)
    var prevCleanup: () => Unit = act(wrapped, param.value)
    val cancel = param.subscribe { p =>
      prevCleanup()
      prevCleanup = act(wrapped, p)
    }
    Cleanup.register(() => { prevCleanup(); cancel() })

  /** Applies an action with a reactive Signal parameter. */
  def action[P](el: dom.Element, act: Action[P], param: Signal[P]): Unit =
    val wrapped = melt.runtime.dom.Conversions.wrapElement(el)
    var prevCleanup: () => Unit = act(wrapped, param.value)
    val cancel = param.subscribe { p =>
      prevCleanup()
      prevCleanup = act(wrapped, p)
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
  ): Unit = mountDynamicOnce(Option(tag), anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementHtmlOption")
  def dynamicElement(
    tag:     Option[HtmlTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag, anchor, scopeId, setup, htmlCreateElement)

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
  ): Unit =
    mountDynamicCore(Some(tag.value), f => tag.subscribe(v => f(Some(v))), anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementSignalNullable")
  def dynamicElement(
    tag:     Signal[HtmlTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    Option(tag.value),
    f => tag.subscribe(t => f(Option(t))),
    anchor,
    scopeId,
    setup,
    htmlCreateElement
  )

  @scala.annotation.targetName("dynamicElementSignalOption")
  def dynamicElement(
    tag:     Signal[Option[HtmlTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.value, tag.subscribe, anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementVar")
  def dynamicElement(
    tag:     Var[HtmlTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Some(tag.value), f => tag.subscribe(v => f(Some(v))), anchor, scopeId, setup, htmlCreateElement)

  @scala.annotation.targetName("dynamicElementVarNullable")
  def dynamicElement(
    tag:     Var[HtmlTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    Option(tag.value),
    f => tag.subscribe(t => f(Option(t))),
    anchor,
    scopeId,
    setup,
    htmlCreateElement
  )

  @scala.annotation.targetName("dynamicElementVarOption")
  def dynamicElement(
    tag:     Var[Option[HtmlTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.value, tag.subscribe, anchor, scopeId, setup, htmlCreateElement)

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
  ): Unit = mountDynamicOnce(Option(tag), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgOption")
  def dynamicElement(
    tag:     Option[SvgTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag, anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgSignal")
  def dynamicElement(
    tag:     Signal[SvgTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Some(tag.value), f => tag.subscribe(v => f(Some(v))), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgSignalNullable")
  def dynamicElement(
    tag:     Signal[SvgTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Option(tag.value), f => tag.subscribe(t => f(Option(t))), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgSignalOption")
  def dynamicElement(
    tag:     Signal[Option[SvgTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.value, tag.subscribe, anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgVar")
  def dynamicElement(
    tag:     Var[SvgTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Some(tag.value), f => tag.subscribe(v => f(Some(v))), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgVarNullable")
  def dynamicElement(
    tag:     Var[SvgTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Option(tag.value), f => tag.subscribe(t => f(Option(t))), anchor, scopeId, setup, svgCreateElement)

  @scala.annotation.targetName("dynamicElementSvgVarOption")
  def dynamicElement(
    tag:     Var[Option[SvgTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.value, tag.subscribe, anchor, scopeId, setup, svgCreateElement)

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
  ): Unit = mountDynamicOnce(Option(tag), anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathOption")
  def dynamicElement(
    tag:     Option[MathTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicOnce(tag, anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathSignal")
  def dynamicElement(
    tag:     Signal[MathTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Some(tag.value), f => tag.subscribe(v => f(Some(v))), anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathSignalNullable")
  def dynamicElement(
    tag:     Signal[MathTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    Option(tag.value),
    f => tag.subscribe(t => f(Option(t))),
    anchor,
    scopeId,
    setup,
    mathCreateElement
  )

  @scala.annotation.targetName("dynamicElementMathSignalOption")
  def dynamicElement(
    tag:     Signal[Option[MathTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.value, tag.subscribe, anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathVar")
  def dynamicElement(
    tag:     Var[MathTag],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit =
    mountDynamicCore(Some(tag.value), f => tag.subscribe(v => f(Some(v))), anchor, scopeId, setup, mathCreateElement)

  @scala.annotation.targetName("dynamicElementMathVarNullable")
  def dynamicElement(
    tag:     Var[MathTag | Null],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(
    Option(tag.value),
    f => tag.subscribe(t => f(Option(t))),
    anchor,
    scopeId,
    setup,
    mathCreateElement
  )

  @scala.annotation.targetName("dynamicElementMathVarOption")
  def dynamicElement(
    tag:     Var[Option[MathTag]],
    anchor:  dom.Comment,
    scopeId: String,
    setup:   dom.Element => Unit
  ): Unit = mountDynamicCore(tag.value, tag.subscribe, anchor, scopeId, setup, mathCreateElement)

  // ── Private helpers ──────────────────────────────────────────────────────

  /** Creates a single element, runs setup, plays intro transition if present, then
    * registers cleanup that plays the outro (if present) before removing the element.
    */
  private def mountDynamicOnce(
    tagName:       Option[String],
    anchor:        dom.Comment,
    scopeId:       String,
    setup:         dom.Element => Unit,
    createElement: String => dom.Element
  ): Unit =
    tagName.foreach { name =>
      val el = createElement(name)
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
    }

  /** Reactive helper: re-creates the element whenever `subscribeFn` fires.
    * Plays intro/outro transitions on enter/leave.
    * Each element is scoped by its own [[OwnerNode]] so that reactive subscriptions
    * created during `setup(el)` are destroyed automatically when the element is swapped.
    */
  private def mountDynamicCore(
    initial:       Option[String],
    subscribeFn:   (Option[String] => Unit) => (() => Unit),
    anchor:        dom.Comment,
    scopeId:       String,
    setup:         dom.Element => Unit,
    createElement: String => dom.Element
  ): Unit =
    var current: Option[dom.Element] = None
    // Holds the OwnerNode for subscriptions created by setup() for the *current* element.
    // Destroyed before each swap so reactive bindings don't fire on detached nodes.
    var elementNode: Option[OwnerNode] = None

    def swap(tag: Option[String]): Unit =
      // Destroy all subscriptions registered by the previous setup call
      elementNode.foreach(_.destroy())
      elementNode = None
      current.foreach { old =>
        current = None
        if TransitionBridge.hasOut(old) then
          playGlobalTransitions(old, intro = false)
          TransitionBridge.playOut(old, () => Option(old.parentNode).foreach(_.removeChild(old)))
        else
          playGlobalTransitions(old, intro = false)
          Option(anchor.parentNode).foreach(_.removeChild(old))
      }
      tag.foreach { name =>
        val el = createElement(name)
        el.classList.add(scopeId)
        // Scope setup's Cleanup.register calls to a per-element OwnerNode
        val (_, node) = Owner.withNew { setup(el) }
        elementNode = Some(node)
        anchor.parentNode.insertBefore(el, anchor)
        if TransitionBridge.hasIn(el) then
          TransitionBridge.playIn(el)
          playGlobalTransitions(el, intro = true)
        else playGlobalTransitions(el, intro = true)
        current = Some(el)
      }

    swap(initial)
    val cancel = subscribeFn(swap)
    Cleanup.register(cancel)
    Cleanup.register(() => {
      elementNode.foreach(_.destroy())
      current.foreach { old =>
        current = None
        if TransitionBridge.hasOut(old) then
          playGlobalTransitions(old, intro = false)
          TransitionBridge.playOut(old, () => Option(old.parentNode).foreach(_.removeChild(old)))
        else
          playGlobalTransitions(old, intro = false)
          Option(anchor.parentNode).foreach(_.removeChild(old))
      }
    })
