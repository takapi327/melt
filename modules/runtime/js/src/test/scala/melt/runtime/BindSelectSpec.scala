/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Tests for [[Bind.textareaValue]], [[Bind.selectValue]], and [[Bind.selectMultipleValue]]. */
class BindSelectSpec extends munit.FunSuite:

  // ── helpers ──────────────────────────────────────────────────────────────

  private def makeTextarea(): dom.html.TextArea =
    dom.document.createElement("textarea").asInstanceOf[dom.html.TextArea]

  private def makeSelect(options: Seq[String], multiple: Boolean = false): dom.html.Select =
    val s = dom.document.createElement("select").asInstanceOf[dom.html.Select]
    if multiple then s.setAttribute("multiple", "")
    options.foreach { v =>
      val o = dom.document.createElement("option").asInstanceOf[dom.html.Option]
      o.value = v
      s.appendChild(o)
    }
    s

  // ── textareaValue ─────────────────────────────────────────────────────────

  test("textareaValue — sets initial value from Var") {
    val v        = Var("hello")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    assertEquals(textarea.value, "hello")
  }

  test("textareaValue — input event updates Var") {
    val v        = Var("")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    textarea.value = "typed"
    textarea.dispatchEvent(new dom.Event("input"))
    assertEquals(v.now(), "typed")
  }

  test("textareaValue — Var change updates textarea") {
    val v        = Var("initial")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    v.set("updated")
    assertEquals(textarea.value, "updated")
  }

  test("textareaValue — no redundant DOM write when values match") {
    val v        = Var("same")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    textarea.value = "changed by user"
    // Var is still "same" — should not overwrite the user's change
    v.set("same")
    assertEquals(textarea.value, "changed by user")
  }

  // ── selectValue ───────────────────────────────────────────────────────────

  test("selectValue — sets initial value from Var") {
    val v      = Var("b")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    assertEquals(select.value, "b")
  }

  test("selectValue — no matching option sets selectedIndex to -1") {
    val v      = Var("z")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    assertEquals(select.selectedIndex, -1)
  }

  test("selectValue — change event updates Var") {
    val v      = Var("a")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    select.value = "b"
    select.dispatchEvent(new dom.Event("change"))
    assertEquals(v.now(), "b")
  }

  test("selectValue — Var change selects matching option") {
    val v      = Var("a")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    v.set("c")
    assertEquals(select.value, "c")
  }

  test("selectValue — Var change to non-existing value sets selectedIndex to -1") {
    val v      = Var("a")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    v.set("z")
    assertEquals(select.selectedIndex, -1)
  }

  // ── selectMultipleValue ───────────────────────────────────────────────────

  test("selectMultipleValue — sets initial selection from Var") {
    val v      = Var(List("a", "c"))
    val select = makeSelect(Seq("a", "b", "c"), multiple = true)
    withOwner { Bind.selectMultipleValue(select, v) }
    val selected =
      (0 until select.options.length)
        .map(i => select.options(i).asInstanceOf[dom.html.Option])
        .filter(_.selected)
        .map(_.value)
        .toList
    assertEquals(selected, List("a", "c"))
  }

  test("selectMultipleValue — change event updates Var") {
    val v      = Var(List("a"))
    val select = makeSelect(Seq("a", "b", "c"), multiple = true)
    withOwner { Bind.selectMultipleValue(select, v) }
    select.options(1).asInstanceOf[dom.html.Option].selected = true
    select.dispatchEvent(new dom.Event("change"))
    assert(v.now().contains("a"), v.now().toString)
    assert(v.now().contains("b"), v.now().toString)
  }

  test("selectMultipleValue — Var change updates selected options") {
    val v      = Var(List("a"))
    val select = makeSelect(Seq("a", "b", "c"), multiple = true)
    withOwner { Bind.selectMultipleValue(select, v) }
    v.set(List("b", "c"))
    val selected =
      (0 until select.options.length)
        .map(i => select.options(i).asInstanceOf[dom.html.Option])
        .filter(_.selected)
        .map(_.value)
        .toList
    assertEquals(selected, List("b", "c"))
  }

  test("selectMultipleValue — empty Var deselects all options") {
    val v      = Var(List("a", "b"))
    val select = makeSelect(Seq("a", "b", "c"), multiple = true)
    withOwner { Bind.selectMultipleValue(select, v) }
    v.set(Nil)
    val selected =
      (0 until select.options.length)
        .map(i => select.options(i).asInstanceOf[dom.html.Option])
        .filter(_.selected)
        .toList
    assert(selected.isEmpty, s"expected no selection, got: $selected")
  }

  // ── helper ────────────────────────────────────────────────────────────────

  private def withOwner[A](body: => A): A =
    val (result, owner) = Owner.withNew(body)
    result
