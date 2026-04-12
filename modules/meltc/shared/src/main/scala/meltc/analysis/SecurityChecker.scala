/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.ast.*

/** Static analysis pass for security-sensitive template patterns
  * (§12.3.11).
  *
  * The checker emits compile-time warnings (not errors) for risky — but
  * not necessarily wrong — constructs that require manual review. The
  * goal is to give developers the same instinct that a seasoned security
  * reviewer would when reading a template.
  *
  * Current checks:
  *
  *   - `<iframe src={url}>` — dynamic iframe sources should always go
  *     through URL validation. `Escape.url` already handles this at
  *     runtime; the warning nudges developers to consider a content
  *     security policy or explicit allow-listing.
  *   - `<object data={url}>` / `<embed src={url}>` — plugin content
  *     surfaces historic ActiveX / Flash attack patterns.
  *   - `<form action={url}>` / `<button formaction={url}>` — dynamic
  *     form targets can exfiltrate user input if the URL is attacker-
  *     controlled.
  *   - `<meta http-equiv="refresh" content={...}>` — redirects driven
  *     by dynamic content should always be reviewed.
  *   - `<a target="_blank">` without `rel="noopener"` — classic
  *     tabnabbing vector.
  *
  * Warnings are returned as `(message, lineNumber)` tuples, the same
  * shape as [[A11yChecker.check]], so `MeltCompiler.compile` can route
  * them through the existing warning-collection pipeline.
  */
object SecurityChecker:

  /** Returns security issues that are severe enough to be **compile errors**.
    *
    * Currently covers:
    *   - `<iframe srcdoc={...}>` — arbitrary HTML injection without TrustedHtml
    */
  def checkErrors(ast: MeltFile, source: String = ""): List[(String, Int)] =
    val errs      = mutable.ListBuffer.empty[(String, Int)]
    val lineIndex = buildLineIndex(source)
    val tagCount  = mutable.Map.empty[String, Int]
    ast.template.foreach(collectErrors(_, errs, source, lineIndex, tagCount))
    errs.toList

  def check(ast: MeltFile, source: String = ""): List[(String, Int)] =
    val w         = mutable.ListBuffer.empty[(String, Int)]
    val lineIndex = buildLineIndex(source)
    val tagCount  = mutable.Map.empty[String, Int]
    ast.template.foreach(checkNode(_, w, source, lineIndex, tagCount))
    w.toList

  private def collectErrors(
    node:      TemplateNode,
    errs:      mutable.ListBuffer[(String, Int)],
    source:    String,
    lineIndex: Array[Int],
    tagCount:  mutable.Map[String, Int]
  ): Unit = node match
    case TemplateNode.Element(tag, attrs, children) =>
      val nth  = tagCount.getOrElse(tag, 0)
      tagCount(tag) = nth + 1
      val line = findNthTagLine(tag, source, lineIndex, nth)
      if tag.toLowerCase == "iframe" && hasDynamicAttr(attrs, "srcdoc") then
        errs += (("<iframe srcdoc={...}> embeds arbitrary HTML at runtime. " +
          "Wrap the value in TrustedHtml to document that it has been sanitised, " +
          "then use bind:innerHTML to emit raw HTML safely.") -> line)
      children.foreach(collectErrors(_, errs, source, lineIndex, tagCount))
    case TemplateNode.Component(_, _, children) =>
      children.foreach(collectErrors(_, errs, source, lineIndex, tagCount))
    case TemplateNode.Head(children) =>
      children.foreach(collectErrors(_, errs, source, lineIndex, tagCount))
    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(collectErrors(_, errs, source, lineIndex, tagCount))
        case _ => ()
      }
    case _ => ()

  private def checkNode(
    node:      TemplateNode,
    w:         mutable.ListBuffer[(String, Int)],
    source:    String,
    lineIndex: Array[Int],
    tagCount:  mutable.Map[String, Int]
  ): Unit = node match
    case TemplateNode.Element(tag, attrs, children) =>
      val nth = tagCount.getOrElse(tag, 0)
      tagCount(tag) = nth + 1
      val line = findNthTagLine(tag, source, lineIndex, nth)
      checkElement(tag, attrs, w, line)
      children.foreach(checkNode(_, w, source, lineIndex, tagCount))

    case TemplateNode.Component(_, _, children) =>
      children.foreach(checkNode(_, w, source, lineIndex, tagCount))

    case TemplateNode.Head(children) =>
      children.foreach(checkNode(_, w, source, lineIndex, tagCount))

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Html(nodes) =>
          nodes.foreach(checkNode(_, w, source, lineIndex, tagCount))
        case _ => ()
      }

    case _ => ()

  private def checkElement(
    tag:   String,
    attrs: List[Attr],
    w:     mutable.ListBuffer[(String, Int)],
    line:  Int
  ): Unit =
    val lower = tag.toLowerCase
    lower match
      case "iframe" =>
        if hasDynamicAttr(attrs, "src") then
          w += (("<iframe> has a dynamic `src` — ensure the URL is validated " +
            "(Escape.url already blocks javascript:/data: schemes, but " +
            "consider a CSP frame-src allow-list).") -> line)
        // srcdoc with dynamic value is a compile *error* (handled by checkErrors)

      case "object" =>
        if hasDynamicAttr(attrs, "data") then
          w += (("<object data={...}> can execute plugin content; validate the " +
            "URL and consider a CSP object-src 'none'.") -> line)

      case "embed" =>
        if hasDynamicAttr(attrs, "src") then
          w += (("<embed src={...}> can execute plugin content; validate the " +
            "URL and consider a CSP object-src 'none'.") -> line)

      case "form" =>
        if hasDynamicAttr(attrs, "action") then
          w += (("<form action={...}> forwards user input to a dynamic URL; " +
            "make sure the URL is trusted or under a same-origin allow-list.") -> line)

      case "button" =>
        if hasDynamicAttr(attrs, "formaction") then
          w += (("<button formaction={...}> forwards user input to a dynamic URL; " +
            "make sure the URL is trusted.") -> line)

      case "meta" =>
        val httpEquivIsRefresh = attrs.exists {
          case Attr.Static("http-equiv", v) => v.equalsIgnoreCase("refresh")
          case _                            => false
        }
        if httpEquivIsRefresh && hasDynamicAttr(attrs, "content") then
          w += (("<meta http-equiv=\"refresh\" content={...}> triggers a redirect; " +
            "dynamic targets can be abused for open-redirect attacks.") -> line)

      case "a" =>
        val targetBlank = attrs.exists {
          case Attr.Static("target", v) => v == "_blank"
          case _                        => false
        }
        if targetBlank then
          val relAttr = attrs.collectFirst {
            case Attr.Static("rel", v) => v.toLowerCase
          }
          val protects =
            relAttr.exists(r => r.contains("noopener") || r.contains("noreferrer"))
          if !protects then
            w += (("<a target=\"_blank\"> without rel=\"noopener\" — the " +
              "opened page can navigate window.opener (reverse tabnabbing). " +
              "Add rel=\"noopener\" (or \"noopener noreferrer\").") -> line)

      case _ => ()

  private def hasDynamicAttr(attrs: List[Attr], name: String): Boolean =
    attrs.exists {
      case Attr.Dynamic(n, _)              => n == name
      case Attr.Shorthand(n)               => n == name
      case Attr.Directive("bind", n, _, _) => n == name
      case _                               => false
    }

  // ── Line-number bookkeeping (shared shape with A11yChecker) ───────────

  private def buildLineIndex(source: String): Array[Int] =
    if source.isEmpty then Array(0)
    else
      val buf = mutable.ArrayBuffer(0)
      var i   = 0
      while i < source.length do
        if source.charAt(i) == '\n' then buf += (i + 1)
        i += 1
      buf.toArray

  private def findNthTagLine(
    tag:       String,
    source:    String,
    lineIndex: Array[Int],
    nth:       Int
  ): Int =
    if source.isEmpty then 0
    else
      val needle = s"<$tag"
      var start  = 0
      var found  = -1
      var i      = 0
      while i <= nth && { found = source.indexOf(needle, start); found >= 0 } do
        if i == nth then return offsetToLine(found, lineIndex)
        start = found + needle.length
        i += 1
      0

  private def offsetToLine(offset: Int, lineIndex: Array[Int]): Int =
    var lo = 0
    var hi = lineIndex.length - 1
    while lo <= hi do
      val mid = (lo + hi) >>> 1
      if lineIndex(mid) <= offset then lo = mid + 1
      else hi                             = mid - 1
    lo
