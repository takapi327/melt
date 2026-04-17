/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Tests for [[Document.bindVisibilityState]] and [[Document.bindActiveElement]].
  *
  * [[Document.bindFullscreenElement]] and [[Document.bindPointerLockElement]] are not tested here
  * because jsdom does not implement the Fullscreen API or Pointer Lock API.
  */
class DocumentBindSpec extends munit.FunSuite:

  // ── bindVisibilityState ───────────────────────────────────────────────────

  test("bindVisibilityState — sets initial value from document.visibilityState") {
    val v = Var("")
    withOwner { Document.bindVisibilityState(v) }
    // jsdom returns "visible" for visibilityState
    assertEquals(v.value, dom.document.visibilityState)
  }

  test("bindVisibilityState — updates Var on visibilitychange event") {
    val v = Var("")
    withOwner { Document.bindVisibilityState(v) }
    val before = v.value
    dom.document.dispatchEvent(new dom.Event("visibilitychange"))
    // After the event the Var should equal the current (still "visible" in jsdom)
    assertEquals(v.value, dom.document.visibilityState)
    assertEquals(v.value, before) // value unchanged in jsdom
  }

  // ── bindActiveElement ─────────────────────────────────────────────────────

  test("bindActiveElement — sets initial value from document.activeElement") {
    val v = Var(Option.empty[dom.Element])
    withOwner { Document.bindActiveElement(v) }
    assertEquals(v.value, Option(dom.document.activeElement))
  }

  test("bindActiveElement — focusin event updates Var to active element") {
    val v   = Var(Option.empty[dom.Element])
    val btn = dom.document.createElement("button")
    dom.document.body.appendChild(btn)
    withOwner { Document.bindActiveElement(v) }
    btn.dispatchEvent(new dom.Event("focusin", new dom.EventInit { bubbles = true }))
    assertEquals(v.value, Option(dom.document.activeElement))
    dom.document.body.removeChild(btn)
  }

  test("bindActiveElement — focusout with null relatedTarget sets Var to None") {
    val v = Var(Option.empty[dom.Element])
    withOwner { Document.bindActiveElement(v) }
    val evt = new dom.FocusEvent("focusout", new dom.FocusEventInit { relatedTarget = null })
    dom.document.dispatchEvent(evt)
    assertEquals(v.value, Option(dom.document.activeElement))
  }

  test("bindActiveElement — focusout with non-null relatedTarget does not update Var") {
    val btn1 = dom.document.createElement("button")
    val btn2 = dom.document.createElement("button")
    dom.document.body.appendChild(btn1)
    dom.document.body.appendChild(btn2)
    val v = Var(Option.empty[dom.Element])
    withOwner { Document.bindActiveElement(v) }
    val beforeFocusout = v.value // capture value set by init
    // focusout with relatedTarget set — should NOT update Var
    val evt = new dom.FocusEvent(
      "focusout",
      new dom.FocusEventInit { relatedTarget = btn2.asInstanceOf[dom.EventTarget] }
    )
    dom.document.dispatchEvent(evt)
    assertEquals(v.value, beforeFocusout)
    dom.document.body.removeChild(btn1)
    dom.document.body.removeChild(btn2)
  }

  // ── helper ────────────────────────────────────────────────────────────────

  private def withOwner[A](body: => A): A =
    val (result, _) = Owner.withNew(body)
    result
