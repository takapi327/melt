/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import scala.collection.mutable

import melt.ast.*

/** Advisory checks for `<form use:form={form}>` auto-binding (warnings only).
  *
  * `use:form` binds each plain `name` control under it to a field of `form`, with
  * the name type-checked at compile time (`FormBindingPass` + the by-name macros).
  * This pass flags the cases where that binding silently will NOT happen, so the
  * mismatch surfaces as an early warning rather than a missing prefill at runtime:
  *
  *   - `use:form` on a non-`<form>` element (it is only meaningful on a form);
  *   - a bindable control (`<input>` / `<select>` / `<textarea>`) with a '''dynamic'''
  *     `name={expr}` — a non-literal name cannot be checked against the model, so
  *     the author must use the hand-written `{...form.field("…")}` spread instead;
  *   - a bindable control with '''no''' `name` at all — nothing to bind (add a
  *     `name`, or `data-form-ignore` to opt out).
  *
  * Reactive regions (`{items.map(i => <input …>)}`) are not descended into: their
  * inputs live inside a `ScalaExpr`, are never auto-bound, and use the hand-written
  * spread — so flagging them would be a false positive.
  */
object FormBindingChecker:

  /** `<input>` types that carry nothing to bind (submit/button/reset/image/file). */
  private val nonBindableInputTypes = Set("submit", "button", "reset", "image", "file")

  def check(ast: MeltFile, source: String = ""): List[(String, Int)] =
    val w         = mutable.ListBuffer.empty[(String, Int)]
    val lineIndex = buildLineIndex(source)
    val tagCount  = mutable.Map.empty[String, Int]
    ast.template.foreach(walk(_, insideForm = false, w, source, lineIndex, tagCount))
    w.toList

  private def walk(
    node:       TemplateNode,
    insideForm: Boolean,
    w:          mutable.ListBuffer[(String, Int)],
    source:     String,
    lineIndex:  Array[Int],
    tagCount:   mutable.Map[String, Int]
  ): Unit =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        // Keep the per-tag occurrence counter in sync with source order (like A11yChecker).
        val nth = tagCount.getOrElse(tag, 0)
        tagCount(tag) = nth + 1
        def line = findNthTagLine(tag, source, lineIndex, nth)

        val declares = declaresUseForm(attrs)
        if declares then
          if tag != "form" then
            w += ((s"use:form is only meaningful on a <form> element, not <$tag> — the binding will be ignored", line))
          // A bare `use:form` takes its form from a sibling `use:enhance={form}`; with
          // neither an explicit `use:form={form}` nor `use:enhance={form}`, there is
          // nothing to bind to.
          if !hasFormExpr(attrs) && !hasEnhanceExpr(attrs) then
            w += ((
              "use:form has no form to bind — write use:form={form}, or add use:enhance={form} on the same element",
              line
            ))

        if insideForm then checkControl(tag, attrs, line, w)

        children.foreach(walk(_, insideForm || declares, w, source, lineIndex, tagCount))

      // A component's slot children are still under the form scope (the pass injects
      // into them); its internal template is opaque and not visible here.
      case TemplateNode.Component(_, _, children) =>
        children.foreach(walk(_, insideForm, w, source, lineIndex, tagCount))

      case _ => () // Text / Expression / InlineTemplate (reactive) — not auto-bound

  /** Warns when a bindable control under `use:form` cannot be auto-bound. */
  private def checkControl(
    tag:   String,
    attrs: List[Attr],
    line:  => Int,
    w:     mutable.ListBuffer[(String, Int)]
  ): Unit =
    val bindable = tag match
      case "input"               => !nonBindableInputTypes.contains(inputType(attrs))
      case "select" | "textarea" => true
      case _                     => false // <option> binds by value via its parent; buttons never bind

    if bindable && !hasIgnore(attrs) then
      if hasDynamicName(attrs) then
        w += ((
          s"<$tag> under use:form has a dynamic name={…}, which cannot be type-checked against the form model — " +
            "use the hand-written {...form.field(\"…\")} spread, or data-form-ignore to opt out",
          line
        ))
      else if !hasStaticName(attrs) then
        w += ((
          s"<$tag> under use:form has no name attribute, so nothing is bound — add a name matching a form field, " +
            "or data-form-ignore to opt out",
          line
        ))

  private def declaresUseForm(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("use", "form", _, _) => true
      case _                                   => false
    }

  private def hasFormExpr(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("use", "form", Some(_), _) => true
      case _                                         => false
    }

  private def hasEnhanceExpr(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("use", "enhance", Some(_), _) => true
      case _                                            => false
    }

  private def inputType(attrs: List[Attr]): String =
    attrs.collectFirst { case Attr.Static("type", v) => v.toLowerCase }.getOrElse("text")

  private def hasStaticName(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Static("name", _) => true
      case _                      => false
    }

  private def hasDynamicName(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Dynamic("name", _) => true
      case Attr.Shorthand("name")  => true
      case _                       => false
    }

  private def hasIgnore(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Static("data-form-ignore", _)   => true
      case Attr.BooleanAttr("data-form-ignore") => true
      case _                                    => false
    }

  // ── Line number utilities (mirrors A11yChecker) ─────────────────────────

  private def buildLineIndex(source: String): Array[Int] =
    if source.isEmpty then return Array(0)
    val offsets = mutable.ArrayBuffer(0)
    var i       = 0
    while i < source.length do
      if source(i) == '\n' then offsets += (i + 1)
      i += 1
    offsets.toArray

  /** Finds the 1-based line of the Nth (0-based) occurrence of `<tag` in source. */
  private def findNthTagLine(tag: String, source: String, lineIndex: Array[Int], nth: Int): Int =
    if source.isEmpty then return 0
    val needle = s"<$tag"
    var idx    = 0
    var count  = 0
    while idx < source.length do
      val found = source.indexOf(needle, idx)
      if found < 0 then return 0
      if count == nth then return offsetToLine(found, lineIndex)
      count += 1
      idx = found + needle.length
    0

  private def offsetToLine(offset: Int, lineIndex: Array[Int]): Int =
    var lo = 0
    var hi = lineIndex.length - 1
    while lo <= hi do
      val mid = (lo + hi) / 2
      if lineIndex(mid) <= offset then lo = mid + 1
      else hi                             = mid - 1
    lo
