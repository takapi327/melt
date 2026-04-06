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

  def check(ast: MeltFile): List[(String, Int)] =
    val warnings = mutable.ListBuffer.empty[(String, Int)]
    ast.template.foreach(checkNode(_, warnings))
    warnings.toList

  private def checkNode(node: TemplateNode, w: mutable.ListBuffer[(String, Int)]): Unit =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        checkElement(tag, attrs, children, w)
        children.foreach(checkNode(_, w))
      case TemplateNode.Component(_, _, children) =>
        children.foreach(checkNode(_, w))
      case TemplateNode.InlineTemplate(parts) =>
        parts.foreach {
          case InlineTemplatePart.Html(nodes) => nodes.foreach(checkNode(_, w))
          case _                             =>
        }
      case _ =>

  private def checkElement(
    tag:      String,
    attrs:    List[Attr],
    children: List[TemplateNode],
    w:        mutable.ListBuffer[(String, Int)]
  ): Unit =
    // ── img alt ──
    if tag == "img" then
      val hasAlt = attrs.exists {
        case Attr.Static("alt", _) | Attr.Dynamic("alt", _) | Attr.Shorthand("alt") => true
        case _ => false
      }
      if !hasAlt then w += (("a11y: <img> element should have an alt attribute", 0))

    // ── heading content ──
    if Set("h1", "h2", "h3", "h4", "h5", "h6").contains(tag) then
      val hasContent = children.exists {
        case TemplateNode.Text(t)       => t.trim.nonEmpty
        case TemplateNode.Expression(_) => true
        case _                          => false
      }
      if !hasContent then w += ((s"a11y: <$tag> element should have text content", 0))

    // ── click on non-interactive ──
    val interactive = Set("button", "a", "input", "select", "textarea", "details", "summary")
    if !interactive.contains(tag) then
      val hasClick = attrs.exists {
        case Attr.EventHandler("click", _) => true
        case _                             => false
      }
      if hasClick then
        val hasRole = attrs.exists {
          case Attr.Static("role", _) | Attr.Dynamic("role", _) => true
          case _                                                 => false
        }
        val hasTabindex = attrs.exists {
          case Attr.Static("tabindex", _) | Attr.Dynamic("tabindex", _) => true
          case _                                                         => false
        }
        if !hasRole || !hasTabindex then
          w += ((s"a11y: <$tag> with onclick should have role and tabindex attributes", 0))

    // ── redundant role ──
    val redundant = Map(
      "button" -> "button", "a" -> "link", "nav" -> "navigation",
      "main" -> "main", "header" -> "banner", "footer" -> "contentinfo"
    )
    redundant.get(tag).foreach { expected =>
      attrs.foreach {
        case Attr.Static("role", r) if r == expected =>
          w += ((s"a11y: <$tag> has redundant role=\"$r\"", 0))
        case _ =>
      }
    }

    // ── video without track ──
    if tag == "video" then
      val hasTrack = children.exists {
        case TemplateNode.Element("track", _, _) => true
        case _                                   => false
      }
      if !hasTrack then w += (("a11y: <video> should have a <track> element for captions", 0))
