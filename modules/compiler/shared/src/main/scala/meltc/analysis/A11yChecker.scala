/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.ast.*

/** Static analysis pass that checks for common accessibility issues in templates. */
object A11yChecker:

  def check(ast: MeltFile, source: String = ""): List[(String, Int)] =
    val w         = mutable.ListBuffer.empty[(String, Int)]
    val lineIndex = buildLineIndex(source)
    val tagCount  = mutable.Map.empty[String, Int]
    ast.template.foreach(checkNode(_, w, source, lineIndex, tagCount))
    w.toList

  private def checkNode(
    node:      TemplateNode,
    w:         mutable.ListBuffer[(String, Int)],
    source:    String,
    lineIndex: Array[Int],
    tagCount:  mutable.Map[String, Int]
  ): Unit =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        checkElement(tag, attrs, children, w, source, lineIndex, tagCount)
        children.foreach(checkNode(_, w, source, lineIndex, tagCount))
      case TemplateNode.Component(_, _, children) =>
        children.foreach(checkNode(_, w, source, lineIndex, tagCount))
      case TemplateNode.InlineTemplate(parts) =>
        parts.foreach {
          case InlineTemplatePart.Html(nodes) => nodes.foreach(checkNode(_, w, source, lineIndex, tagCount))
          case _                              =>
        }
      case _ =>

  private def checkElement(
    tag:       String,
    attrs:     List[Attr],
    children:  List[TemplateNode],
    w:         mutable.ListBuffer[(String, Int)],
    source:    String,
    lineIndex: Array[Int],
    tagCount:  mutable.Map[String, Int]
  ): Unit =
    val nth = tagCount.getOrElse(tag, 0)
    tagCount(tag) = nth + 1
    val line = findNthTagLine(tag, source, lineIndex, nth)

    if tag == "img" then
      val hasAlt = attrs.exists {
        case Attr.Static("alt", _) | Attr.Dynamic("alt", _) | Attr.Shorthand("alt") => true
        case _                                                                      => false
      }
      if !hasAlt then w += ((s"a11y: <img> element should have an alt attribute", line))

    if Set("h1", "h2", "h3", "h4", "h5", "h6").contains(tag) then
      val hasContent = children.exists {
        case TemplateNode.Text(t)       => t.trim.nonEmpty
        case TemplateNode.Expression(_) => true
        case _                          => false
      }
      if !hasContent then w += ((s"a11y: <$tag> element should have text content", line))

    val interactive = Set("button", "a", "input", "select", "textarea", "details", "summary")
    if !interactive.contains(tag) then
      val hasClick = attrs.exists {
        case Attr.EventHandler("click", _) => true
        case _                             => false
      }
      if hasClick then
        val hasRole = attrs.exists {
          case Attr.Static("role", _) | Attr.Dynamic("role", _) => true
          case _                                                => false
        }
        val hasTabindex = attrs.exists {
          case Attr.Static("tabindex", _) | Attr.Dynamic("tabindex", _) => true
          case _                                                        => false
        }
        if !hasRole || !hasTabindex then
          w += ((s"a11y: <$tag> with onclick should have role and tabindex attributes", line))

    val redundant = Map(
      "button" -> "button",
      "a"      -> "link",
      "nav"    -> "navigation",
      "main"   -> "main",
      "header" -> "banner",
      "footer" -> "contentinfo"
    )
    redundant.get(tag).foreach { expected =>
      attrs.foreach {
        case Attr.Static("role", r) if r == expected =>
          w += ((s"a11y: <$tag> has redundant role=\"$r\"", line))
        case _ =>
      }
    }

    if tag == "video" then
      val hasTrack = children.exists {
        case TemplateNode.Element("track", _, _) => true
        case _                                   => false
      }
      if !hasTrack then w += ((s"a11y: <video> should have a <track> element for captions", line))

  // ── Line number utilities ──────────────────────────────────────────────

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
