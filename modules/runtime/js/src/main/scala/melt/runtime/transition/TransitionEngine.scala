/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.transition

import scala.scalajs.js

import org.scalajs.dom

/** Drives CSS-keyframe or `requestAnimationFrame` animations for transitions.
  *
  * Call [[run]] to start a transition. If another transition is already running
  * on the same element, it is aborted first.
  *
  * The engine stores an abort handle on the element under the JS property
  * `_meltAbort` so that a subsequent [[run]] call can cancel the previous one.
  *
  * For `transition:` directives (which support mid-animation reversal), the
  * engine also records timing metadata so [[currentT]] can return the visual
  * `t` value at any point during the animation.
  */
object TransitionEngine:

  /** Tracks the OS/browser `prefers-reduced-motion: reduce` setting.
    * When `true`, all transitions are skipped (duration set to 0) so that
    * users who are sensitive to motion do not see animated content.
    */
  private val reducedMotionMq = dom.window.matchMedia("(prefers-reduced-motion: reduce)")
  private var _reducedMotion  = reducedMotionMq.matches

  reducedMotionMq.addEventListener(
    "change",
    (e: dom.Event) => _reducedMotion = e.asInstanceOf[js.Dynamic].matches.asInstanceOf[Boolean]
  )

  /** Returns `true` when the user has requested reduced motion. */
  def prefersReducedMotion: Boolean = _reducedMotion

  // Keys for per-element JS properties
  private val KeyAbort     = "_meltAbort"
  private val KeyStartTime = "_meltStartTime"
  private val KeyDuration  = "_meltDuration"
  private val KeyDelay     = "_meltDelay"
  private val KeyIsIntro   = "_meltIsIntro"
  private val KeyEasing    = "_meltEasing"
  private val KeyStartFrom = "_meltStartFrom"
  private val KeyCurrentT  = "_meltCurrentT" // written per-frame in tick path

  private def dispatchTransitionEvent(el: dom.Element, name: String): Unit =
    val event = new dom.Event(name, new dom.EventInit { bubbles = true })
    el.dispatchEvent(event)

  private var _counter: Int = 0

  private def nextId(): String =
    _counter += 1
    s"melt-t${ _counter }"

  /** Lazily created `<style>` element that holds generated `@keyframes` rules. */
  private var _styleEl: Option[dom.html.Style] = None

  private def styleEl(): dom.html.Style =
    _styleEl match
      case Some(el) if dom.document.head.contains(el) => el
      case _                                          =>
        val el = dom.document.createElement("style").asInstanceOf[dom.html.Style]
        el.setAttribute("data-melt-transitions", "")
        dom.document.head.appendChild(el)
        _styleEl = Some(el)
        el

  /** Returns the current visual `t` value of any in-progress animation on `el`.
    *
    * For the tick path, `t` is stored directly each frame.
    * For the CSS path, `t` is computed from stored timing metadata.
    * Returns `None` if no transition metadata is recorded.
    *
    * Used by [[melt.runtime.TransitionBridge]] to implement mid-animation reversal.
    */
  def currentT(el: dom.Element): Option[Double] =
    val dyn = el.asInstanceOf[js.Dynamic]

    // Tick path stores t directly per-frame
    val storedT = dyn.selectDynamic(KeyCurrentT)
    if !js.isUndefined(storedT) then return Some(storedT.asInstanceOf[Double])

    // CSS path: compute from stored timing metadata
    val startTimeV = dyn.selectDynamic(KeyStartTime)
    if js.isUndefined(startTimeV) then return None

    val startTime = startTimeV.asInstanceOf[Double]
    val duration  = dyn.selectDynamic(KeyDuration).asInstanceOf[Double]
    val delay     = dyn.selectDynamic(KeyDelay).asInstanceOf[Double]
    val isIntro   = dyn.selectDynamic(KeyIsIntro).asInstanceOf[Boolean]
    val startFrom = dyn.selectDynamic(KeyStartFrom).asInstanceOf[Double]
    val finalT    = if isIntro then 1.0 else 0.0
    val easing    = dyn.selectDynamic(KeyEasing).asInstanceOf[Double => Double]

    val now     = js.Date.now()
    val elapsed = math.max(now - startTime - delay, 0.0)
    val local   = math.min(elapsed / duration, 1.0)
    val t       = startFrom + (finalT - startFrom) * easing(local)
    Some(t)

  /** Starts a transition animation on `el`.
    *
    * @param el          the target DOM element
    * @param config      [[TransitionConfig]] produced by a [[Transition]] function
    * @param intro       `true` for enter (t goes toward 1), `false` for leave (t goes toward 0)
    * @param startFrom   starting visual `t` value (0.0–1.0).  The default (`-1.0`) resolves to
    *                    `0.0` for intro and `1.0` for outro, giving a full animation.
    *                    Pass the value from [[currentT]] to implement smooth mid-animation reversal.
    * @param onDone      called when the animation finishes; may be called asynchronously
    * @param emitEvents  when `false`, skips dispatching `introstart`/`introend`/`outrostart`/`outroend`
    *                    events.  Set to `false` for `animate:` (position-change) animations that are
    *                    neither enter nor leave and should not appear as transition lifecycle events.
    */
  def run(
    el:         dom.Element,
    config:     TransitionConfig,
    intro:      Boolean,
    startFrom:  Double = -1.0, // -1.0 = auto (0.0 for intro, 1.0 for outro)
    onDone:     () => Unit = () => (),
    emitEvents: Boolean = true
  ): Unit =
    abortCurrent(el)

    // When the user has requested reduced motion, skip the animation entirely.
    if _reducedMotion then
      if emitEvents then dispatchTransitionEvent(el, if intro then "introstart" else "outrostart")
      // Apply final visual state immediately via the css/tick function
      val finalT = if intro then 1.0 else 0.0
      val finalU = 1.0 - finalT
      config.css.foreach { cssFn =>
        el.asInstanceOf[dom.html.Element].style.cssText += cssFn(finalT, finalU)
      }
      config.tick.foreach(_(finalT, finalU))
      if emitEvents then dispatchTransitionEvent(el, if intro then "introend" else "outroend")
      onDone()
      return

    val sf = if startFrom < 0.0 then (if intro then 0.0 else 1.0) else startFrom

    if emitEvents then dispatchTransitionEvent(el, if intro then "introstart" else "outrostart")

    var done = false

    def finish(): Unit =
      if !done then
        done = true
        el.asInstanceOf[js.Dynamic].updateDynamic(KeyAbort)(js.undefined)
        if emitEvents then dispatchTransitionEvent(el, if intro then "introend" else "outroend")
        onDone()

    config.css match
      case Some(cssFn) =>
        val abort = runCss(el, cssFn, config, intro, sf, finish)
        el.asInstanceOf[js.Dynamic].updateDynamic(KeyAbort)(abort)
      case None =>
        config.tick match
          case Some(tickFn) =>
            val abort = runTick(el, tickFn, config, intro, sf, finish)
            el.asInstanceOf[js.Dynamic].updateDynamic(KeyAbort)(abort)
          case None =>
            val delay = config.delay
            if delay > 0 then dom.window.setTimeout(() => finish(), delay.toDouble)
            else finish()

  /** Cancels any in-progress transition on `el`. */
  private def abortCurrent(el: dom.Element): Unit =
    val dyn   = el.asInstanceOf[js.Dynamic]
    val abort = dyn.selectDynamic(KeyAbort)
    if !js.isUndefined(abort) then
      abort.asInstanceOf[js.Function0[Unit]]()
      dyn.updateDynamic(KeyAbort)(js.undefined)

  /** CSS `@keyframes` animation path.
    *
    * Pre-bakes the easing function into the keyframe percentage positions so
    * the browser's own `linear` timing function reproduces the desired curve.
    * When `startFrom` is non-zero the animation begins at that visual `t` value
    * and the duration is scaled proportionally to the remaining distance.
    * The generated rule is removed from the `<style>` element when the
    * animation ends.
    *
    * @return a `js.Function0[Unit]` that aborts the animation when called
    */
  private def runCss(
    el:        dom.Element,
    cssFn:     (Double, Double) => String,
    config:    TransitionConfig,
    intro:     Boolean,
    startFrom: Double,
    finish:    () => Unit
  ): js.Function0[Unit] =
    val easing   = config.easing
    val delay    = config.delay
    val duration = config.duration
    val animName = nextId()

    val finalT      = if intro then 1.0 else 0.0
    val distance    = math.abs(finalT - startFrom)
    val adjDuration = math.max((distance * duration).toInt, 1)

    val steps  = 60
    val frames = (0 to steps)
      .map { i =>
        val local = i.toDouble / steps
        val t     = startFrom + (finalT - startFrom) * easing(local)
        val u     = 1.0 - t
        val pct   = (local * 100).toInt
        s"$pct% { ${ cssFn(t, u) } }"
      }
      .mkString("\n")

    val rule    = s"@keyframes $animName {\n$frames\n}"
    val sheet   = styleEl().sheet.asInstanceOf[js.Dynamic]
    val ruleIdx = sheet.insertRule(rule, sheet.cssRules.length).asInstanceOf[Int]

    val htmlEl    = el.asInstanceOf[dom.html.Element]
    val dyn       = el.asInstanceOf[js.Dynamic]
    val wallStart = js.Date.now()
    dyn.updateDynamic(KeyStartTime)(wallStart)
    dyn.updateDynamic(KeyDuration)(adjDuration.toDouble)
    dyn.updateDynamic(KeyDelay)(delay.toDouble)
    dyn.updateDynamic(KeyIsIntro)(intro)
    dyn.updateDynamic(KeyStartFrom)(startFrom)
    dyn.updateDynamic(KeyEasing)(easing.asInstanceOf[js.Any])
    dyn.updateDynamic(KeyCurrentT)(js.undefined) // CSS path uses metadata-computed value

    htmlEl.style.setProperty("animation", s"$animName ${ adjDuration }ms ${ delay }ms linear both")
    htmlEl.style.setProperty("animation-fill-mode", "both")

    var cleanedUp = false

    def cleanup(): Unit =
      if !cleanedUp then
        cleanedUp = true
        htmlEl.style.removeProperty("animation")
        htmlEl.style.removeProperty("animation-fill-mode")
        try sheet.deleteRule(ruleIdx)
        catch { case _: Throwable => }
        finish()

    val onEnd: js.Function1[dom.Event, Unit] = (_: dom.Event) => cleanup()
    el.addEventListener("animationend", onEnd)

    // Fallback timeout in case animationend does not fire
    dom.window.setTimeout(
      () => { el.removeEventListener("animationend", onEnd); cleanup() },
      (delay + adjDuration + 50).toDouble
    )

    () => { el.removeEventListener("animationend", onEnd); cleanup() }

  /** `requestAnimationFrame` tick animation path.
    *
    * Drives the `tick(t, u)` callback at each frame. `t` moves from `startFrom`
    * toward `1` (intro) or toward `0` (outro), shaped by the easing function.
    * The current visual `t` is stored on the element each frame so [[currentT]]
    * can return it for mid-animation reversal.
    *
    * @return a `js.Function0[Unit]` that aborts the animation when called
    */
  private def runTick(
    el:        dom.Element,
    tickFn:    (Double, Double) => Unit,
    config:    TransitionConfig,
    intro:     Boolean,
    startFrom: Double,
    finish:    () => Unit
  ): js.Function0[Unit] =
    val easing   = config.easing
    val delay    = config.delay
    val duration = config.duration

    val finalT      = if intro then 1.0 else 0.0
    val distance    = math.abs(finalT - startFrom)
    val adjDuration = math.max(distance * duration, 1.0)

    var rafId   = 0
    var aborted = false
    val dyn     = el.asInstanceOf[js.Dynamic]

    def loop(startTime: Double): js.Function1[Double, Unit] = (now: Double) =>
      if !aborted then
        val elapsed = now - startTime
        val local   = math.min(elapsed / adjDuration, 1.0)
        val t       = startFrom + (finalT - startFrom) * easing(local)
        val u       = 1.0 - t
        dyn.updateDynamic(KeyCurrentT)(t) // store for mid-reversal
        tickFn(t, u)
        if local < 1.0 then rafId = dom.window.requestAnimationFrame(loop(startTime)).toInt
        else finish()

    def start(): Unit =
      if !aborted then rafId = dom.window.requestAnimationFrame((now: Double) => loop(now)(now)).toInt

    if delay > 0 then dom.window.setTimeout(() => start(), delay.toDouble)
    else start()

    () => { aborted = true; dom.window.cancelAnimationFrame(rafId) }
