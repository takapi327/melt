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

  test("textareaValue — sets initial value from State") {
    val v        = State("hello")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    assertEquals(textarea.value, "hello")
  }

  test("textareaValue — input event updates State") {
    val v        = State("")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    textarea.value = "typed"
    textarea.dispatchEvent(new dom.Event("input"))
    assertEquals(v.value, "typed")
  }

  test("textareaValue — State change updates textarea") {
    val v        = State("initial")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    v.set("updated")
    assertEquals(textarea.value, "updated")
  }

  test("textareaValue — no redundant DOM write when values match") {
    val v        = State("same")
    val textarea = makeTextarea()
    withOwner { Bind.textareaValue(textarea, v) }
    textarea.value = "changed by user"
    // State is still "same" — should not overwrite the user's change
    v.set("same")
    assertEquals(textarea.value, "changed by user")
  }

  // ── selectValue ───────────────────────────────────────────────────────────

  test("selectValue — sets initial value from State") {
    val v      = State("b")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    assertEquals(select.value, "b")
  }

  test("selectValue — no matching option sets selectedIndex to -1") {
    val v      = State("z")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    assertEquals(select.selectedIndex, -1)
  }

  test("selectValue — change event updates State") {
    val v      = State("a")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    select.value = "b"
    select.dispatchEvent(new dom.Event("change"))
    assertEquals(v.value, "b")
  }

  test("selectValue — State change selects matching option") {
    val v      = State("a")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    v.set("c")
    assertEquals(select.value, "c")
  }

  test("selectValue — State change to non-existing value sets selectedIndex to -1") {
    val v      = State("a")
    val select = makeSelect(Seq("a", "b", "c"))
    withOwner { Bind.selectValue(select, v) }
    v.set("z")
    assertEquals(select.selectedIndex, -1)
  }

  // ── selectMultipleValue ───────────────────────────────────────────────────

  test("selectMultipleValue — sets initial selection from State") {
    val v      = State(List("a", "c"))
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

  test("selectMultipleValue — change event updates State") {
    val v      = State(List("a"))
    val select = makeSelect(Seq("a", "b", "c"), multiple = true)
    withOwner { Bind.selectMultipleValue(select, v) }
    select.options(1).asInstanceOf[dom.html.Option].selected = true
    select.dispatchEvent(new dom.Event("change"))
    assert(v.value.contains("a"), v.value.toString)
    assert(v.value.contains("b"), v.value.toString)
  }

  test("selectMultipleValue — State change updates selected options") {
    val v      = State(List("a"))
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

  test("selectMultipleValue — empty State deselects all options") {
    val v      = State(List("a", "b"))
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
