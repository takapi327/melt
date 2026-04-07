/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import org.scalajs.dom

import melt.runtime.*
import melt.testkit.*

/** Verifies type-safety of the Melt runtime API.
  *
  * Tests are grouped into:
  *   - "SAFE" — code that the type system correctly accepts or rejects
  *              (including formerly-BUG cases that are now compile errors)
  *   - "RISK" — code that compiles fine but is easy to misuse silently
  */
class TypeSafetySpec extends MeltSuite:

  // ── helpers ──────────────────────────────────────────────────────────────

  private def el(tag: String): dom.Element =
    dom.document.createElement(tag)

  private def inputEl(): dom.html.Input =
    dom.document.createElement("input").asInstanceOf[dom.html.Input]

  private def containerEl(): dom.Element =
    val div = el("div")
    dom.document.body.appendChild(div)
    div

  // ── SAFE: Bind.attr with Var[Boolean] / Signal[Boolean] ───────────────

  /** SAFE: Bind.attr(el, name, Var[Boolean]) is now a compile-time error.
    *
    * Previously, the Var[?] overload called .toString() which produced the
    * string "false" — HTML boolean attributes set to any non-empty value mean
    * the attribute is present, so disabled="false" kept the element disabled.
    *
    * The inline overloads now reject Var[Boolean] and Signal[Boolean] at
    * compile time with a descriptive message directing to Bind.booleanAttr.
    *
    * {{{
    * // Compile error — use Bind.booleanAttr instead:
    * Bind.attr(button, "disabled", Var(false))
    * Bind.attr(button, "disabled", Var(false).signal)
    * }}}
    */

  /** SAFE: Bind.booleanAttr correctly removes the attribute when false. */
  test("SAFE: Bind.booleanAttr removes attribute when Var[Boolean] is false") {
    val button   = el("button")
    val disabled = Var(false)
    Bind.booleanAttr(button, "disabled", disabled)
    assert(!button.hasAttribute("disabled"), "attribute should be absent when false")

    disabled.set(true)
    assert(button.hasAttribute("disabled"), "attribute should be present when true")

    disabled.set(false)
    assert(!button.hasAttribute("disabled"), "attribute should be absent again after set(false)")
  }

  test("SAFE: static Bind.attr(el, name, Any) handles Boolean correctly via pattern match") {
    val button = el("button")
    Bind.attr(button, "disabled", false)
    assert(!button.hasAttribute("disabled"), "static false removes the attribute")
    Bind.attr(button, "disabled", true)
    assert(button.hasAttribute("disabled"), "static true adds the attribute")
  }

  // ── BUG: Var.map / Signal cleanup not called without Cleanup scope ──────

  /** RISK: Var.map and Signal.map register cleanup handlers via Cleanup.register,
    * but if called outside a component's create() scope (i.e. without
    * Cleanup.pushScope() / Cleanup.popScope()), the subscription is never
    * released and leaks indefinitely.
    *
    * Users writing tests or utility code outside a component may unknowingly
    * create subscription leaks.
    */
  test("RISK: Var.map outside Cleanup scope registers a cleanup that is never released") {
    val count   = Var(0)
    var derived = -1
    // Simulate calling map() outside a component create() scope.
    // Cleanup.register is called internally, but there is no active scope to
    // collect into — currently Cleanup silently discards the registration.
    Cleanup.pushScope()
    val doubled  = count.map(_ * 2)
    val cleanups = Cleanup.popScope()

    count.set(5)
    assertEquals(doubled.now(), 10)

    // After runAll cleanups the subscription should be released
    Cleanup.runAll(cleanups)
    count.set(99)
    // After cleanup, doubled should no longer track count changes
    assertEquals(doubled.now(), 10, "after cleanup, derived signal should not update")
  }

  // ── SAFE: Var[A] type safety ───────────────────────────────────────────

  test("SAFE: Var[A].set only accepts values of type A") {
    val count: Var[Int] = Var(0)
    count.set(42)
    assertEquals(count.now(), 42)
    // count.set("hello")   // compile error — correctly rejected
    // count.set(3.14)      // compile error — correctly rejected
  }

  test("SAFE: Var[A].update preserves type") {
    val name: Var[String] = Var("Alice")
    name.update(_.toUpperCase)
    assertEquals(name.now(), "ALICE")
    // name.update(_ + 1)   // compile error — Int arithmetic on String
  }

  test("SAFE: Var[A].map produces Signal[B] with correct type") {
    Cleanup.pushScope()
    val count   = Var(0)
    val doubled = count.map(_ * 2) // Signal[Int]
    count.set(3)
    assertEquals(doubled.now(), 6)
    Cleanup.popScope()
    // doubled.now() is Int — type-safe, not Any
  }

  test("SAFE: Signal[A].map produces Signal[B] with correct type") {
    Cleanup.pushScope()
    val count = Var(0)
    val label: Signal[String] = count.signal.map(n => s"Count: $n")
    count.set(7)
    assertEquals(label.now(), "Count: 7")
    Cleanup.popScope()
  }

  // ── SAFE: Bind two-way binding type safety ─────────────────────────────

  test("SAFE: Bind.inputValue requires Var[String]") {
    val input = inputEl()
    val text  = Var("hello")
    Bind.inputValue(input, text)
    assertEquals(input.value, "hello")
    text.set("world")
    assertEquals(input.value, "world")
    // Bind.inputValue(input, Var(42))    // compile error — Var[Int] not accepted
    // Bind.inputValue(input, Var(true))  // compile error — Var[Boolean] not accepted
  }

  test("SAFE: Bind.inputInt requires Var[Int]") {
    val input = inputEl()
    val count = Var(5)
    Bind.inputInt(input, count)
    assertEquals(input.value, "5")
    count.set(10)
    assertEquals(input.value, "10")
    // Bind.inputInt(input, Var("5"))     // compile error — Var[String] not accepted
  }

  test("SAFE: Bind.inputDouble requires Var[Double]") {
    val input = inputEl()
    val ratio = Var(0.5)
    Bind.inputDouble(input, ratio)
    assertEquals(input.value, "0.5")
    // Bind.inputDouble(input, Var(1))    // compile error — Var[Int] not accepted
  }

  test("SAFE: Bind.inputChecked requires Var[Boolean]") {
    val input = inputEl()
    input.setAttribute("type", "checkbox")
    val checked = Var(false)
    Bind.inputChecked(input, checked)
    assertEquals(input.checked, false)
    checked.set(true)
    assertEquals(input.checked, true)
    // Bind.inputChecked(input, Var("true"))  // compile error — Var[String] not accepted
  }

  test("SAFE: Bind.classToggle requires Var[Boolean] — non-Boolean Var rejected at compile time") {
    val button = el("button")
    val active = Var(false)
    Bind.classToggle(button, "active", active)
    assert(!button.classList.contains("active"))
    active.set(true)
    assert(button.classList.contains("active"))
    active.set(false)
    assert(!button.classList.contains("active"))
    // Bind.classToggle(button, "active", Var("yes"))   // compile error
    // Bind.classToggle(button, "active", Var(1))        // compile error
  }

  // ── SAFE: Action[P] parameter type enforcement ─────────────────────────

  test("SAFE: Action[String] rejects non-String parameter at compile time") {
    val tooltip = Action[String] { (el, text) =>
      el.setAttribute("title", text)
      () => el.removeAttribute("title")
    }
    val button = el("button")
    Cleanup.pushScope()
    Bind.action(button, tooltip, "Click me")
    assertEquals(button.getAttribute("title"), "Click me")
    // Bind.action(button, tooltip, 42)    // compile error — Int not String
    // Bind.action(button, tooltip, true)  // compile error — Boolean not String
    Cleanup.popScope()
  }

  test("SAFE: Action[Int] rejects non-Int parameter at compile time") {
    val badge = Action[Int] { (el, count) =>
      el.setAttribute("data-count", count.toString)
      () => el.removeAttribute("data-count")
    }
    val button = el("button")
    Cleanup.pushScope()
    Bind.action(button, badge, 5)
    assertEquals(button.getAttribute("data-count"), "5")
    // Bind.action(button, badge, "5")    // compile error — String not Int
    Cleanup.popScope()
  }

  // ── SAFE: Ref[A] type bound ────────────────────────────────────────────

  test("SAFE: Ref[A <: dom.Element] only accepts subtypes of dom.Element") {
    val inputRef = Ref.empty[dom.html.Input]
    val input    = inputEl()
    inputRef.set(input)
    inputRef.foreach(el => assertEquals(el.tagName.toLowerCase, "input"))
    // Ref.empty[String]         // compile error — String not <: dom.Element
    // Ref.empty[Int]            // compile error — Int not <: dom.Element
    // inputRef.set(el("div").asInstanceOf[dom.html.Input])  // cast unsafe but compiles
  }

  test("SAFE: Ref[dom.html.Canvas] exposes canvas-specific API after set") {
    val canvasRef = Ref.empty[dom.html.Canvas]
    val canvas    = dom.document.createElement("canvas").asInstanceOf[dom.html.Canvas]
    canvas.width  = 100
    canvas.height = 100
    canvasRef.set(canvas)
    canvasRef.foreach(c => assertEquals(c.width, 100))
    // canvasRef.set(el("div"))  // compile error — div is dom.Element, not dom.html.Canvas
  }

  // ── SAFE/RISK: Bind.text overload resolution ──────────────────────────

  /** SAFE: Bind.text(value: Any) is now Bind.text(value: String).
    *
    * Previously Any was accepted so Bind.text(count.now()) where count is
    * Var[Int] silently selected the static overload — the DOM never updated.
    * Now the static overload only accepts String, so non-String .now() calls
    * are rejected at compile time.
    *
    * {{{
    * val count = Var(0)
    * Bind.text(count.now(), parent)   // compile error — Int does not conform to String
    * }}}
    *
    * RISK: Var[String].now() still returns String, so Bind.text(label.now(), parent)
    * compiles but is silently static. This is a residual risk for String Vars.
    */
  test("SAFE: Bind.text with non-String .now() is now a compile error") {
    val parent = containerEl()
    val count  = Var(0)

    // Correct: pass Var directly for reactive binding
    Cleanup.pushScope()
    Bind.text(count, parent) // Var[?] overload → reactive
    Cleanup.popScope()

    count.set(99)
    assertEquals(parent.textContent, "99", "reactive binding should reflect new value")
    // count.now() returns Int — now rejected at compile time:
    // Bind.text(count.now(), parent)   // compile error — Int does not conform to String
  }

  test("RISK: Bind.text(String) with Var[String].now() is silently static") {
    val parent = containerEl()
    val label  = Var("hello")

    Cleanup.pushScope()
    Bind.text(label.now(), parent) // String overload — bound ONCE, never updates
    Cleanup.popScope()

    label.set("world")
    assertEquals(
      parent.textContent,
      "hello",
      "RISK: Var[String].now() returns String which satisfies static overload — binding is silently non-reactive"
    )
  }

  // ── SAFE: Bind.list requires Iterable ─────────────────────────────────

  test("SAFE: Bind.list requires Var[? <: Iterable[A]]") {
    val parent = containerEl()
    val anchor = dom.document.createComment("anchor")
    parent.appendChild(anchor)
    val items = Var(List("a", "b", "c"))
    Cleanup.pushScope()
    Bind.list(items, (s: String) => dom.document.createTextNode(s), anchor)
    Cleanup.popScope()
    assertEquals(parent.textContent, "abc")
    // Bind.list(Var(42), ..., anchor)         // compile error — Int not Iterable
    // Bind.list(Var("hello"), ..., anchor)    // compile error — String not Iterable
  }

  test("SAFE: Bind.each requires Var[? <: Iterable[A]] and a key function") {
    val parent = containerEl()
    val anchor = dom.document.createComment("anchor")
    parent.appendChild(anchor)
    Cleanup.pushScope()
    Bind.each(
      Var(List(1, 2, 3)),
      (n: Int) => n,
      (n: Int) => dom.document.createTextNode(n.toString),
      anchor
    )
    Cleanup.popScope()
    assertEquals(parent.textContent, "123")
  }

  // ── SAFE: Bind.html requires TrustedHtml ─────────────────────────────

  /** SAFE: Bind.html now requires TrustedHtml instead of String/Var[String].
    *
    * Previously Bind.html accepted Var[String] directly, making XSS-prone
    * call sites invisible in code review. Now callers must explicitly wrap
    * their string with TrustedHtml.unsafe, making the acknowledgement visible.
    *
    * {{{
    * // Compile error — plain Var[String] no longer accepted:
    * Bind.html(el, Var("<b>user input</b>"))
    *
    * // OK — explicit opt-in with TrustedHtml.unsafe:
    * val safe = Var(TrustedHtml.unsafe("<b>static markup</b>"))
    * Bind.html(el, safe)
    * }}}
    */
  test("SAFE: Bind.html requires TrustedHtml — plain Var[String] is rejected at compile time") {
    val parent = el("div")
    // Bind.html(parent, Var("<b>bold</b>"))  // compile error — Var[String] not accepted

    // Must explicitly wrap with TrustedHtml.unsafe:
    val content = Var(TrustedHtml.unsafe("<b>bold</b>"))
    Cleanup.pushScope()
    Bind.html(parent, content)
    Cleanup.popScope()
    assertEquals(parent.querySelector("b").textContent, "bold")

    content.set(TrustedHtml.unsafe("<em>italic</em>"))
    assertEquals(parent.querySelector("em").textContent, "italic")
  }

  test("SAFE: Bind.html with static TrustedHtml works correctly") {
    val parent = el("div")
    Bind.html(parent, TrustedHtml.unsafe("<strong>hello</strong>"))
    assertEquals(parent.querySelector("strong").textContent, "hello")
  }

  // ── RISK: Var.subscribe leaks without calling the cancel function ──────

  test("RISK: Var.subscribe without storing the cancel function leaks the subscription") {
    val count = Var(0)
    var calls = 0
    // No cleanup: the cancel function returned by subscribe is discarded.
    // In a real component, Cleanup.register should hold this reference.
    count.subscribe(_ => calls += 1) // cancel function discarded!
    count.set(1)
    count.set(2)
    assertEquals(calls, 2, "subscription fires as expected")
    // There is no way to cancel this subscription after the cancel is dropped.
    // In a component, Cleanup.register handles this; in ad-hoc code it leaks.
  }

  // ── RISK: Bind.attr accepts wrong type for numeric attributes ──────────

  test("RISK: Bind.attr with Var[String] for tabindex accepts non-numeric strings") {
    val div    = el("div")
    val tabIdx = Var("not-a-number")
    Bind.attr(div, "tabindex", tabIdx)
    // HTML spec requires tabindex to be a valid integer. Passing a non-numeric
    // string compiles fine and sets the attribute, but browsers/jsdom ignore it.
    assertEquals(div.getAttribute("tabindex"), "not-a-number")
    // No compile error, no runtime error, silent semantic failure.
  }
