/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import org.scalajs.dom

/** Resolves ARIA roles for DOM elements.
  *
  * Implements a subset of the ARIA in HTML specification:
  * https://www.w3.org/TR/html-aria/
  *
  * Resolution order:
  *   1. Explicit `role` attribute on the element
  *   2. Implicit role derived from the HTML tag (and attributes where relevant)
  *
  * Phase 1 covers the most commonly used roles. The `name` filter
  * (Accessible Name and Description Computation) is deferred to a later phase.
  */
private[testkit] object AriaUtils:

  /** Returns the effective ARIA role of `el`, or `None` if the element has no role. */
  def resolveRole(el: dom.Element): Option[String] =
    Option(el.getAttribute("role")).filter(_.nonEmpty).orElse(implicitRole(el))

  private def implicitRole(el: dom.Element): Option[String] =
    el.tagName.toLowerCase match
      // ── Buttons & links ──────────────────────────────────────────────────
      case "button" => Some("button")
      case "a"      => if el.hasAttribute("href") then Some("link") else None
      // ── Headings ─────────────────────────────────────────────────────────
      case "h1" | "h2" | "h3" | "h4" | "h5" | "h6" => Some("heading")
      // ── Form controls ────────────────────────────────────────────────────
      case "input"    => inputRole(el)
      case "textarea" => Some("textbox")
      case "select"   =>
        val isListbox =
          el.hasAttribute("multiple") ||
            el.getAttribute("size").toIntOption.exists(_ > 1)
        if isListbox then Some("listbox") else Some("combobox")
      case "datalist" => Some("listbox")
      case "fieldset" => Some("group")
      case "meter"    => Some("meter")
      case "progress" => Some("progressbar")
      case "output"   => Some("status")
      // ── Images ───────────────────────────────────────────────────────────
      case "img" =>
        val alt = el.getAttribute("alt")
        if alt == null then Some("img")
        else if alt.isEmpty then Some("presentation")
        else Some("img")
      // ── Lists ────────────────────────────────────────────────────────────
      case "ul" | "ol" | "menu" => Some("list")
      case "li"                 => Some("listitem")
      // ── Tables ───────────────────────────────────────────────────────────
      case "table"                     => Some("table")
      case "thead" | "tfoot" | "tbody" => Some("rowgroup")
      case "tr"                        => Some("row")
      case "td"                        => Some("cell")
      case "th"                        =>
        // scope="row" → rowheader; scope="col" or absent → columnheader
        if Option(el.getAttribute("scope")).map(_.toLowerCase).contains("row")
        then Some("rowheader")
        else Some("columnheader")
      case "caption" => Some("caption")
      // ── Landmarks ────────────────────────────────────────────────────────
      case "nav"    => Some("navigation")
      case "main"   => Some("main")
      case "header" =>
        // banner only when NOT a descendant of article/aside/main/nav/section
        if isLandmarkContext(el) then None else Some("banner")
      case "footer" =>
        // contentinfo only when NOT a descendant of article/aside/main/nav/section
        if isLandmarkContext(el) then None else Some("contentinfo")
      case "aside"  => Some("complementary")
      case "search" => Some("search")
      // ── Sectioning ───────────────────────────────────────────────────────
      case "article" => Some("article")
      case "figure"  => Some("figure")
      case "section" =>
        val hasLabel =
          el.hasAttribute("aria-label") ||
            el.hasAttribute("aria-labelledby") ||
            el.hasAttribute("title")
        if hasLabel then Some("region") else None
      // ── Interactive ──────────────────────────────────────────────────────
      case "dialog" => Some("dialog")
      // ── Misc ─────────────────────────────────────────────────────────────
      case "hr"   => Some("separator")
      case "form" =>
        val hasLabel =
          el.hasAttribute("aria-label") ||
            el.hasAttribute("aria-labelledby") ||
            el.hasAttribute("title")
        if hasLabel then Some("form") else None
      case _ => None

  /** Returns true if `el` is a descendant of article/aside/main/nav/section,
    * which suppresses the landmark role of header/footer. */
  private def isLandmarkContext(el: dom.Element): Boolean =
    val sectioning = Set("article", "aside", "main", "nav", "section")
    var ancestor   = el.parentNode
    while ancestor != null do
      ancestor match
        case e: dom.Element if sectioning.contains(e.tagName.toLowerCase) => return true
        case _                                                            =>
      ancestor = ancestor.parentNode
    false

  private def inputRole(el: dom.Element): Option[String] =
    Option(el.getAttribute("type")).map(_.toLowerCase).getOrElse("text") match
      case "checkbox"                              => Some("checkbox")
      case "radio"                                 => Some("radio")
      case "number"                                => Some("spinbutton")
      case "search"                                => Some("searchbox")
      case "range"                                 => Some("slider")
      case "button" | "submit" | "reset" | "image" => Some("button")
      // No corresponding ARIA role
      case "hidden" | "color" | "file"                           => None
      case "date" | "time" | "month" | "week" | "datetime-local" => None
      // text-like: combobox when a <datalist> is linked via list attribute, otherwise textbox
      case _ =>
        if el.hasAttribute("list") then Some("combobox") else Some("textbox")
