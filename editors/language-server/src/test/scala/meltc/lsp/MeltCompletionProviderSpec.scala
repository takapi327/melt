/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

class MeltCompletionProviderSpec extends munit.FunSuite:

  // ── Script section ─────────────────────────────────────────────────────────

  test("script completions include Var") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Script)
    assert(items.exists(_.getLabel == "Var"), "Var should be present")
  }

  test("script completions include Signal") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Script)
    assert(items.exists(_.getLabel == "Signal"), "Signal should be present")
  }

  test("script completions include import-runtime snippet") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Script)
    assert(items.exists(_.getLabel == "import-runtime"))
  }

  test("script completions include var-decl snippet with insert text") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Script)
    val item  = items.find(_.getLabel == "var-decl").get
    assert(item.getInsertText.contains("Var("), s"insert text: ${ item.getInsertText }")
  }

  test("script completions include TrustedHtml") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Script)
    assert(items.exists(_.getLabel == "TrustedHtml"))
  }

  // ── Template section ───────────────────────────────────────────────────────

  test("template completions include common HTML tags") {
    val items  = MeltCompletionProvider.completionsFor(MeltSection.Template)
    val labels = items.map(_.getLabel).toSet
    assert(labels.contains("<div>"), "should include <div>")
    assert(labels.contains("<button>"), "should include <button>")
    assert(labels.contains("<p>"), "should include <p>")
  }

  test("template completions include reactive expression snippet") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Template)
    assert(items.exists(_.getLabel == "{expr}"))
  }

  test("void tags use self-closing snippet") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Template)
    val input = items.find(_.getLabel == "<input>").get
    assert(input.getInsertText.contains("/>"), s"input insert text: ${ input.getInsertText }")
    assert(!input.getInsertText.contains("</input>"), "void tag must not have closing tag")
  }

  test("non-void tags wrap content in closing tag") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Template)
    val div   = items.find(_.getLabel == "<div>").get
    assert(div.getInsertText.contains("</div>"), s"div insert text: ${ div.getInsertText }")
  }

  // ── Style section ──────────────────────────────────────────────────────────

  test("style completions include CSS properties") {
    val items  = MeltCompletionProvider.completionsFor(MeltSection.Style)
    val labels = items.map(_.getLabel).toSet
    assert(labels.contains("color"), "should include color")
    assert(labels.contains("background-color"), "should include background-color")
    assert(labels.contains("display"), "should include display")
  }

  test("style completions include @media snippet") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Style)
    assert(items.exists(_.getLabel == "@media"))
  }

  test("style completions include :hover pseudo-class") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Style)
    assert(items.exists(_.getLabel == ":hover"))
  }

  test("CSS property snippets end with semicolon in insert text") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Style)
    val color = items.find(_.getLabel == "color").get
    assert(color.getInsertText.endsWith(";"), s"color insert text: ${ color.getInsertText }")
  }

  // ── Unknown / top-level ────────────────────────────────────────────────────

  test("unknown section completions include script-block scaffold") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Unknown)
    assert(items.exists(_.getLabel == "script-block"))
  }

  test("unknown section completions include style-block scaffold") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Unknown)
    assert(items.exists(_.getLabel == "style-block"))
  }

  test("melt-component snippet covers all three sections") {
    val items = MeltCompletionProvider.completionsFor(MeltSection.Unknown)
    val comp  = items.find(_.getLabel == "melt-component").get
    val text  = comp.getInsertText
    assert(text.contains("<script lang=\"scala\">"), "should contain script tag")
    assert(text.contains("<style>"), "should contain style tag")
  }

  // ── Snippet format ─────────────────────────────────────────────────────────

  test("all snippet items use InsertTextFormat.Snippet") {
    import org.eclipse.lsp4j.InsertTextFormat
    for section <- List(MeltSection.Script, MeltSection.Template, MeltSection.Style, MeltSection.Unknown) do
      val items = MeltCompletionProvider.completionsFor(section)
      for item <- items if item.getInsertText != null do
        assertEquals(
          item.getInsertTextFormat,
          InsertTextFormat.Snippet,
          s"${ item.getLabel } in $section should use Snippet format"
        )
  }
