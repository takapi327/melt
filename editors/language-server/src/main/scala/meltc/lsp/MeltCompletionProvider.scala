/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import org.eclipse.lsp4j.*

/** Provides Melt-specific completion items for each section of a .melt file.
  *
  * These completions are always available regardless of whether Metals is installed,
  * and cover the Melt-specific APIs, HTML template syntax, and CSS properties.
  *
  * ==Sections==
  *   - [[MeltSection.Script]]   → Melt runtime types and common snippets
  *   - [[MeltSection.Template]] → HTML tags and `{expr}` binding syntax
  *   - [[MeltSection.Style]]    → CSS property declarations and selectors
  *   - [[MeltSection.Unknown]]  → Top-level .melt structure snippets
  */
object MeltCompletionProvider:

  def completionsFor(section: MeltSection): List[CompletionItem] =
    section match
      case MeltSection.Script   => scriptCompletions
      case MeltSection.Template => templateCompletions
      case MeltSection.Style    => styleCompletions
      case MeltSection.Unknown  => meltStructureCompletions

  // ── Script section ────────────────────────────────────────────────────────

  private val scriptCompletions: List[CompletionItem] = List(
    snippet(
      "import-runtime",
      "import melt.runtime.*",
      "Import melt runtime",
      CompletionItemKind.Module
    ),
    typeItem("Var", "Reactive mutable variable — Var(initialValue)"),
    typeItem("Signal", "Read-only reactive signal derived from one or more Vars"),
    typeItem("Bind", "DOM binding utilities for reactive updates"),
    typeItem("TrustedHtml", "XSS-safe HTML wrapper — use TrustedHtml.unsafe(html)"),
    snippet(
      "var-decl",
      "val ${1:name} = Var(${2:initialValue})",
      "Reactive variable declaration",
      CompletionItemKind.Snippet
    ),
    snippet(
      "computed",
      "val ${1:computed} = Signal { ${2:expr} }",
      "Computed signal derived from reactive state",
      CompletionItemKind.Snippet
    ),
    snippet(
      "effect",
      "Signal.effect { ${1:sideEffect} }",
      "Reactive side-effect (runs whenever dependencies change)",
      CompletionItemKind.Snippet
    ),
    snippet(
      "trusted-html",
      "TrustedHtml.unsafe(${1:html})",
      "Wrap a trusted HTML string (XSS acknowledgement)",
      CompletionItemKind.Snippet
    ),
    snippet(
      "def-handler",
      "def ${1:handler}(): Unit =\n  ${2:state}.update(${3:_ + 1})",
      "Event handler that updates reactive state",
      CompletionItemKind.Snippet
    )
  )

  // ── Template section ───────────────────────────────────────────────────────

  private val templateCompletions: List[CompletionItem] =
    val voidTags = Set("input", "img", "br", "hr", "meta", "link", "source", "track", "wbr")

    val htmlTags = List(
      "div",
      "p",
      "span",
      "button",
      "input",
      "ul",
      "li",
      "ol",
      "h1",
      "h2",
      "h3",
      "h4",
      "h5",
      "h6",
      "a",
      "form",
      "label",
      "select",
      "option",
      "textarea",
      "section",
      "article",
      "header",
      "footer",
      "nav",
      "main",
      "table",
      "tr",
      "td",
      "th",
      "thead",
      "tbody",
      "tfoot",
      "img",
      "video",
      "audio",
      "source",
      "br",
      "hr"
    ).map { tag =>
      if voidTags.contains(tag) then
        snippet(s"<$tag>", s"<$tag $$0/>", s"<$tag> (void element)", CompletionItemKind.Property)
      else snippet(s"<$tag>", s"<$tag>$$0</$tag>", s"<$tag> element", CompletionItemKind.Property)
    }

    val meltSnippets = List(
      snippet(
        "{expr}",
        "{${1:expr}}",
        "Reactive expression — re-renders when reactive dependencies change",
        CompletionItemKind.Snippet
      ),
      snippet(
        "onclick",
        "onclick={${1:handler}}",
        "Click event binding",
        CompletionItemKind.Snippet
      ),
      snippet(
        "oninput",
        "oninput={${1:e => handler(e.target.value)}}",
        "Input event binding",
        CompletionItemKind.Snippet
      )
    )

    htmlTags ++ meltSnippets

  // ── Style section ─────────────────────────────────────────────────────────

  private val styleCompletions: List[CompletionItem] =
    val properties = List(
      "color",
      "background-color",
      "background",
      "font-size",
      "font-weight",
      "font-family",
      "margin",
      "margin-top",
      "margin-right",
      "margin-bottom",
      "margin-left",
      "padding",
      "padding-top",
      "padding-right",
      "padding-bottom",
      "padding-left",
      "display",
      "flex-direction",
      "justify-content",
      "align-items",
      "align-self",
      "flex-wrap",
      "flex-grow",
      "flex-shrink",
      "flex-basis",
      "grid-template-columns",
      "grid-template-rows",
      "grid-column",
      "grid-row",
      "gap",
      "width",
      "height",
      "min-width",
      "min-height",
      "max-width",
      "max-height",
      "border",
      "border-radius",
      "border-color",
      "border-width",
      "border-style",
      "position",
      "top",
      "right",
      "bottom",
      "left",
      "z-index",
      "opacity",
      "visibility",
      "transform",
      "transition",
      "animation",
      "cursor",
      "pointer-events",
      "user-select",
      "overflow",
      "overflow-x",
      "overflow-y",
      "text-align",
      "text-decoration",
      "text-transform",
      "line-height",
      "letter-spacing",
      "box-shadow",
      "text-shadow",
      "list-style",
      "list-style-type",
      "white-space",
      "word-break",
      "word-wrap"
    ).map(prop => snippet(prop, s"$prop: $$0;", s"CSS $prop", CompletionItemKind.Property))

    val atRules = List(
      snippet(
        "@media",
        "@media (${1:max-width: 768px}) {\n  $0\n}",
        "Responsive media query",
        CompletionItemKind.Keyword
      ),
      snippet(
        "@keyframes",
        "@keyframes ${1:name} {\n  from { $2 }\n  to { $0 }\n}",
        "CSS keyframes animation",
        CompletionItemKind.Keyword
      )
    )

    val pseudoClasses = List(
      snippet(":hover", "&:hover {\n  $0\n}", "Hover pseudo-class", CompletionItemKind.Snippet),
      snippet(":focus", "&:focus {\n  $0\n}", "Focus pseudo-class", CompletionItemKind.Snippet),
      snippet(":active", "&:active {\n  $0\n}", "Active pseudo-class", CompletionItemKind.Snippet),
      snippet(":disabled", "&:disabled {\n  $0\n}", "Disabled pseudo-class", CompletionItemKind.Snippet),
      snippet(":nth-child", "&:nth-child(${1:n}) {\n  $0\n}", "nth-child selector", CompletionItemKind.Snippet)
    )

    properties ++ atRules ++ pseudoClasses

  // ── Top-level Melt structure ───────────────────────────────────────────────

  private val meltStructureCompletions: List[CompletionItem] = List(
    snippet(
      "melt-component",
      "<script lang=\"scala\">\n$1\n</script>\n\n$2\n\n<style>\n$0\n</style>",
      "Full .melt single-file component scaffold",
      CompletionItemKind.Snippet
    ),
    snippet(
      "script-block",
      "<script lang=\"scala\">\n$0\n</script>",
      "Scala script block",
      CompletionItemKind.Snippet
    ),
    snippet(
      "style-block",
      "<style>\n$0\n</style>",
      "CSS style block",
      CompletionItemKind.Snippet
    )
  )

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def snippet(
    label:      String,
    insertText: String,
    detail:     String,
    kind:       CompletionItemKind
  ): CompletionItem =
    val item = CompletionItem(label)
    item.setKind(kind)
    item.setInsertText(insertText)
    item.setInsertTextFormat(InsertTextFormat.Snippet)
    item.setDetail(detail)
    item

  private def typeItem(name: String, detail: String): CompletionItem =
    val item = CompletionItem(name)
    item.setKind(CompletionItemKind.Class)
    item.setDetail(detail)
    item
