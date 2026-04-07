/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import org.scalajs.dom

/** Computes the accessible name of a DOM element.
  *
  * Implements a practical subset of the W3C
  * [[https://www.w3.org/TR/accname-1.1/ Accessible Name and Description Computation]] (ANDC) spec.
  *
  * Resolution order (per ANDC §4.3):
  *   1. `aria-labelledby` — textContent of referenced elements (space-joined)
  *   2. `aria-label` attribute
  *   3. Element-native name source (varies by role/tag — see [[nativeName]])
  *   4. `title` attribute
  *
  * Known limitations (jsdom constraints):
  *   - CSS-generated content (`::before`/`::after`) is not included.
  *   - `visibility: hidden` elements are not excluded from `aria-labelledby` resolution
  *     (jsdom does not compute CSS).
  */
private[testkit] object AccessibleName:

  /** Returns the accessible name of `el`, scoped to `root` for id lookups.
    * Returns an empty string if no name can be determined.
    */
  def compute(el: dom.Element, root: dom.Element): String =
    // 1. aria-labelledby
    ariaLabelledBy(el, root)
      // 2. aria-label
      .orElse(Option(el.getAttribute("aria-label")).map(_.trim).filter(_.nonEmpty))
      // 3. element-native name
      .orElse(nativeName(el, root))
      // 4. title
      .orElse(Option(el.getAttribute("title")).map(_.trim).filter(_.nonEmpty))
      .getOrElse("")

  private def ariaLabelledBy(el: dom.Element, root: dom.Element): Option[String] =
    Option(el.getAttribute("aria-labelledby"))
      .filter(_.nonEmpty)
      .map { ids =>
        ids.split("\\s+")
          .filter(_.nonEmpty)
          .flatMap(id => Option(root.querySelector(s"#$id")).map(_.textContent.trim))
          .mkString(" ")
          .trim
      }
      .filter(_.nonEmpty)

  /** Element-native accessible name source per ARIA in HTML spec. */
  private def nativeName(el: dom.Element, root: dom.Element): Option[String] =
    el.tagName.toLowerCase match
      // Elements whose name comes from their text content
      case "button" | "a" | "h1" | "h2" | "h3" | "h4" | "h5" | "h6" |
          "td" | "th" | "li" | "option" =>
        textOf(el)

      // input — name source depends on type
      case "input" =>
        Option(el.getAttribute("type")).map(_.toLowerCase).getOrElse("text") match
          case "image"                    => Option(el.getAttribute("alt")).map(_.trim).filter(_.nonEmpty)
          case "button" | "submit" | "reset" =>
            Option(el.getAttribute("value")).map(_.trim).filter(_.nonEmpty)
          case _ => labelFor(el, root)

      case "select" | "textarea" => labelFor(el, root)

      // <fieldset> → <legend> child
      case "fieldset" =>
        Option(el.querySelector("legend")).flatMap(textOf)

      // <table> → <caption> child
      case "table" =>
        Option(el.querySelector("caption")).flatMap(textOf)

      // <figure> → <figcaption> child
      case "figure" =>
        Option(el.querySelector("figcaption")).flatMap(textOf)

      case _ => None

  /** Finds the label text for a labellable element via `<label for="id">` or wrapping `<label>`. */
  private def labelFor(el: dom.Element, root: dom.Element): Option[String] =
    // label[for="id"]
    val byFor = Option(el.getAttribute("id"))
      .filter(_.nonEmpty)
      .flatMap(id => Option(root.querySelector(s"label[for='$id']")))
      .flatMap(textOf)

    // wrapping <label>
    lazy val byWrap =
      var ancestor = el.parentNode
      var result: Option[String] = None
      while ancestor != null && result.isEmpty do
        ancestor match
          case e: dom.Element if e.tagName.toLowerCase == "label" =>
            result = textOf(e)
          case _ =>
        ancestor = ancestor.parentNode
      result

    byFor.orElse(byWrap)

  private def textOf(el: dom.Element): Option[String] =
    val t = el.textContent.trim
    if t.nonEmpty then Some(t) else None
