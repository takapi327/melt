/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** A container for HTML attributes to be forwarded to a component's root element. */
case class HtmlAttrs(entries: Map[String, String]):
  def apply(el: dom.Element): Unit =
    entries.foreach { case (k, v) => el.setAttribute(k, v) }

object HtmlAttrs:
  val empty:                           HtmlAttrs = HtmlAttrs(Map.empty)
  def apply(pairs: (String, String)*): HtmlAttrs = HtmlAttrs(pairs.toMap)

// ── Attribute map builder helpers ────────────────────────────────────────

private[runtime] object AttrBuilder:
  def addStr(
    b:    scala.collection.mutable.Builder[(String, String), Map[String, String]],
    name: String,
    v:    Option[String]
  ): Unit =
    v.foreach(b += name -> _)

  /** Adds an int option to a builder. */
  def addInt(
    b:    scala.collection.mutable.Builder[(String, String), Map[String, String]],
    name: String,
    v:    Option[Int]
  ): Unit =
    v.foreach(n => b += name -> n.toString)

  /** Adds a boolean option to a builder (present = empty string, absent = not included). */
  def addBool(
    b:    scala.collection.mutable.Builder[(String, String), Map[String, String]],
    name: String,
    v:    Option[Boolean]
  ): Unit =
    v.foreach(flag => if flag then b += name -> "")

// ── Base trait ───────────────────────────────────────────────────────────

trait HtmlProps:
  var id:        Option[String]  = None
  var title:     Option[String]  = None
  var hidden:    Option[Boolean] = None
  var tabindex:  Option[Int]     = None
  var role:      Option[String]  = None
  var lang:      Option[String]  = None
  var dir:       Option[String]  = None
  var draggable: Option[Boolean] = None

  private var _html:              HtmlAttrs = HtmlAttrs.empty
  def html:                       HtmlAttrs = _html
  def withHtml(attrs: HtmlAttrs): this.type = { _html = attrs; this }

  /** Collects all non-None attribute fields into an HtmlAttrs map. Subtraits override to add their own. */
  def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    b ++= _html.entries
    HtmlAttrs(b.result())

  protected def collectGlobalAttrs(b: scala.collection.mutable.Builder[(String, String), Map[String, String]]): Unit =
    import AttrBuilder.*
    addStr(b, "id", id)
    addStr(b, "title", this.title)
    addBool(b, "hidden", hidden)
    addInt(b, "tabindex", tabindex)
    addStr(b, "role", role)
    addStr(b, "lang", lang)
    addStr(b, "dir", dir)
    addBool(b, "draggable", draggable)

// ── Element-specific traits ──────────────────────────────────────────────

trait ButtonHtmlProps extends HtmlProps:
  var disabled:       Option[Boolean] = None
  var buttonType:     Option[String]  = None
  var name:           Option[String]  = None
  var value:          Option[String]  = None
  var form:           Option[String]  = None
  var formAction:     Option[String]  = None
  var formMethod:     Option[String]  = None
  var formNoValidate: Option[Boolean] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addBool(b, "disabled", disabled)
    addStr(b, "type", buttonType)
    addStr(b, "name", name)
    addStr(b, "value", value)
    addStr(b, "form", form)
    addStr(b, "formaction", formAction)
    addStr(b, "formmethod", formMethod)
    addBool(b, "formnovalidate", formNoValidate)
    b ++= html.entries
    HtmlAttrs(b.result())

trait InputHtmlProps extends HtmlProps:
  var inputType:    Option[String]  = None
  var placeholder:  Option[String]  = None
  var name:         Option[String]  = None
  var value:        Option[String]  = None
  var disabled:     Option[Boolean] = None
  var readonly:     Option[Boolean] = None
  var required:     Option[Boolean] = None
  var maxLength:    Option[Int]     = None
  var minLength:    Option[Int]     = None
  var min:          Option[String]  = None
  var max:          Option[String]  = None
  var step:         Option[String]  = None
  var pattern:      Option[String]  = None
  var accept:       Option[String]  = None
  var multiple:     Option[Boolean] = None
  var checked:      Option[Boolean] = None
  var autocomplete: Option[String]  = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addStr(b, "type", inputType)
    addStr(b, "placeholder", placeholder)
    addStr(b, "name", name)
    addStr(b, "value", value)
    addBool(b, "disabled", disabled)
    addBool(b, "readonly", readonly)
    addBool(b, "required", required)
    addInt(b, "maxlength", maxLength)
    addInt(b, "minlength", minLength)
    addStr(b, "min", min)
    addStr(b, "max", max)
    addStr(b, "step", step)
    addStr(b, "pattern", pattern)
    addStr(b, "accept", accept)
    addBool(b, "multiple", multiple)
    addBool(b, "checked", checked)
    addStr(b, "autocomplete", autocomplete)
    b ++= html.entries
    HtmlAttrs(b.result())

trait AnchorHtmlProps extends HtmlProps:
  var href:     Option[String] = None
  var target:   Option[String] = None
  var rel:      Option[String] = None
  var download: Option[String] = None
  var hreflang: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addStr(b, "href", href)
    addStr(b, "target", target)
    addStr(b, "rel", rel)
    addStr(b, "download", download)
    addStr(b, "hreflang", hreflang)
    b ++= html.entries
    HtmlAttrs(b.result())

trait FormHtmlProps extends HtmlProps:
  var action:     Option[String]  = None
  var method:     Option[String]  = None
  var enctype:    Option[String]  = None
  var formTarget: Option[String]  = None
  var novalidate: Option[Boolean] = None
  var name:       Option[String]  = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addStr(b, "action", action)
    addStr(b, "method", method)
    addStr(b, "enctype", enctype)
    addStr(b, "target", formTarget)
    addBool(b, "novalidate", novalidate)
    addStr(b, "name", name)
    b ++= html.entries
    HtmlAttrs(b.result())

trait ImgHtmlProps extends HtmlProps:
  var src:         Option[String] = None
  var alt:         Option[String] = None
  var width:       Option[Int]    = None
  var height:      Option[Int]    = None
  var loading:     Option[String] = None
  var decoding:    Option[String] = None
  var srcset:      Option[String] = None
  var sizes:       Option[String] = None
  var crossorigin: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addStr(b, "src", src)
    addStr(b, "alt", alt)
    addInt(b, "width", width)
    addInt(b, "height", height)
    addStr(b, "loading", loading)
    addStr(b, "decoding", decoding)
    addStr(b, "srcset", srcset)
    addStr(b, "sizes", sizes)
    addStr(b, "crossorigin", crossorigin)
    b ++= html.entries
    HtmlAttrs(b.result())

trait SelectHtmlProps extends HtmlProps:
  var name:     Option[String]  = None
  var disabled: Option[Boolean] = None
  var required: Option[Boolean] = None
  var multiple: Option[Boolean] = None
  var size:     Option[Int]     = None
  var form:     Option[String]  = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addStr(b, "name", name)
    addBool(b, "disabled", disabled)
    addBool(b, "required", required)
    addBool(b, "multiple", multiple)
    addInt(b, "size", size)
    addStr(b, "form", form)
    b ++= html.entries
    HtmlAttrs(b.result())

trait TextAreaHtmlProps extends HtmlProps:
  var name:        Option[String]  = None
  var placeholder: Option[String]  = None
  var rows:        Option[Int]     = None
  var cols:        Option[Int]     = None
  var disabled:    Option[Boolean] = None
  var readonly:    Option[Boolean] = None
  var required:    Option[Boolean] = None
  var maxLength:   Option[Int]     = None
  var minLength:   Option[Int]     = None
  var wrap:        Option[String]  = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    import AttrBuilder.*
    addStr(b, "name", name)
    addStr(b, "placeholder", placeholder)
    addInt(b, "rows", rows)
    addInt(b, "cols", cols)
    addBool(b, "disabled", disabled)
    addBool(b, "readonly", readonly)
    addBool(b, "required", required)
    addInt(b, "maxlength", maxLength)
    addInt(b, "minlength", minLength)
    addStr(b, "wrap", wrap)
    b ++= html.entries
    HtmlAttrs(b.result())
