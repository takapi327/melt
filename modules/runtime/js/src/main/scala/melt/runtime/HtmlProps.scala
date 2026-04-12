/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

import AttrBuilder.*

/** A container for HTML attributes to be forwarded to a component's root element.
  *
  * {{{
  * val attrs = HtmlAttrs("id" -> "main", "class" -> "container")
  * attrs.apply(el) // sets id="main" class="container" on the element
  * }}}
  */
case class HtmlAttrs(entries: Map[String, String]):

  /** Applies all entries as attributes to the given DOM element. */
  def apply(el: dom.Element): Unit =
    entries.foreach { case (k, v) => el.setAttribute(k, v) }

object HtmlAttrs:
  val empty:                           HtmlAttrs = HtmlAttrs(Map.empty)
  def apply(pairs: (String, String)*): HtmlAttrs = HtmlAttrs(pairs.toMap)

// ── Attribute map builder helpers ────────────────────────────────────────

/** Internal helpers for building `Map[String, String]` from `Option` fields. */
private[runtime] object AttrBuilder:

  /** Adds a `String` option as an attribute entry. */
  def addStr(
    b:    scala.collection.mutable.Builder[(String, String), Map[String, String]],
    name: String,
    v:    Option[String]
  ): Unit =
    v.foreach(b += name -> _)

  /** Adds an `Int` option as a stringified attribute entry. */
  def addInt(
    b:    scala.collection.mutable.Builder[(String, String), Map[String, String]],
    name: String,
    v:    Option[Int]
  ): Unit =
    v.foreach(n => b += name -> n.toString)

  /** Adds a `Boolean` option as an attribute entry.
    * `true` emits the attribute with an empty value (e.g. `disabled=""`);
    * `false` is not included.
    */
  def addBool(
    b:    scala.collection.mutable.Builder[(String, String), Map[String, String]],
    name: String,
    v:    Option[Boolean]
  ): Unit =
    v.foreach(flag => if flag then b += name -> "")

// ── Base trait ───────────────────────────────────────────────────────────

/** Base trait for component Props that support HTML attribute forwarding.
  *
  * Provides global HTML attributes (`id`, `title`, `hidden`, `tabindex`,
  * `role`, `lang`, `dir`, `draggable`) common to all element types,
  * plus a [[HtmlAttrs]] container for arbitrary pass-through attributes
  * (e.g. `aria-*`, `data-*`).
  *
  * Extend one of the element-specific sub-traits (e.g. [[ButtonHtmlProps]],
  * [[InputHtmlProps]]) to also expose element-specific attributes.
  *
  * {{{
  * case class Props(label: String) extends ButtonHtmlProps
  *
  * // Caller:
  * val p = Props("Click me")
  * p.disabled = Some(true)
  * p.id = Some("my-btn")
  * // In template: <button {...props.allHtmlAttrs}>{props.label}</button>
  * }}}
  */
trait HtmlProps:
  /** The unique identifier of the element. Maps to the `id` HTML attribute. */
  var id: Option[String] = None

  /** Advisory information about the element, often shown as a tooltip. Maps to the `title` HTML attribute. */
  var title: Option[String] = None

  /** When `true`, the element is not rendered. Maps to the `hidden` HTML attribute. */
  var hidden: Option[Boolean] = None

  /** The tab order of the element for keyboard navigation. Maps to the `tabindex` HTML attribute. */
  var tabindex: Option[Int] = None

  /** The ARIA role of the element for accessibility. Maps to the `role` HTML attribute. */
  var role: Option[String] = None

  /** The language of the element's content (e.g. `"en"`, `"ja"`). Maps to the `lang` HTML attribute. */
  var lang: Option[String] = None

  /** The text directionality of the element's content (`"ltr"`, `"rtl"`, `"auto"`). Maps to the `dir` HTML attribute. */
  var dir: Option[String] = None

  /** Whether the element is draggable. Maps to the `draggable` HTML attribute. */
  var draggable: Option[Boolean] = None

  private var _html:              HtmlAttrs = HtmlAttrs.empty
  def html:                       HtmlAttrs = _html
  def withHtml(attrs: HtmlAttrs): this.type = { _html = attrs; this }

  /** Collects all non-None attribute fields + custom [[HtmlAttrs]] into a single map.
    * Element-specific sub-traits override this to include their own fields.
    */
  def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)
    b ++= _html.entries
    HtmlAttrs(b.result())

  /** Adds global HTML attributes to the builder. Called by sub-trait overrides. */
  protected def collectGlobalAttrs(b: scala.collection.mutable.Builder[(String, String), Map[String, String]]): Unit =

    addStr(b, "id", id)
    addStr(b, "title", this.title)
    addBool(b, "hidden", hidden)
    addInt(b, "tabindex", tabindex)
    addStr(b, "role", role)
    addStr(b, "lang", lang)
    addStr(b, "dir", dir)
    addBool(b, "draggable", draggable)

// ── Element-specific traits ──────────────────────────────────────────────

/** Props for components whose root element is `<button>`.
  *
  * Exposes button-specific HTML attributes:
  * `disabled`, `type` (via `buttonType`), `name`, `value`,
  * `form`, `formaction`, `formmethod`, `formnovalidate`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/button MDN: button]]
  */
trait ButtonHtmlProps extends HtmlProps:
  /** Disables the button when `true`. Maps to the `disabled` HTML attribute. */
  var disabled: Option[Boolean] = None

  /** The type of button (`"button"`, `"submit"`, `"reset"`). Maps to the `type` HTML attribute. */
  var buttonType: Option[String] = None

  /** The name of the button, submitted as part of form data. Maps to the `name` HTML attribute. */
  var name: Option[String] = None

  /** The value of the button, submitted as part of form data. Maps to the `value` HTML attribute. */
  var value: Option[String] = None

  /** Associates the button with a `<form>` element by its `id`. Maps to the `form` HTML attribute. */
  var form: Option[String] = None

  /** Overrides the form's `action` URL on submission. Maps to the `formaction` HTML attribute. */
  var formAction: Option[String] = None

  /** Overrides the form's HTTP method (`"get"` or `"post"`). Maps to the `formmethod` HTML attribute. */
  var formMethod: Option[String] = None

  /** When `true`, bypasses form validation on submission. Maps to the `formnovalidate` HTML attribute. */
  var formNoValidate: Option[Boolean] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

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

/** Props for components whose root element is `<input>`.
  *
  * Exposes input-specific HTML attributes:
  * `type` (via `inputType`), `placeholder`, `name`, `value`,
  * `disabled`, `readonly`, `required`, `maxlength`, `minlength`,
  * `min`, `max`, `step`, `pattern`, `accept`, `multiple`,
  * `checked`, `autocomplete`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/input MDN: input]]
  */
trait InputHtmlProps extends HtmlProps:
  /** The type of input control (e.g. `"text"`, `"email"`, `"password"`, `"checkbox"`). Maps to the `type` HTML attribute. */
  var inputType: Option[String] = None

  /** Hint text displayed when the field is empty. Maps to the `placeholder` HTML attribute. */
  var placeholder: Option[String] = None

  /** The name of the input, submitted as part of form data. Maps to the `name` HTML attribute. */
  var name: Option[String] = None

  /** The current value of the input. Maps to the `value` HTML attribute. */
  var value: Option[String] = None

  /** Disables the input when `true`. Maps to the `disabled` HTML attribute. */
  var disabled: Option[Boolean] = None

  /** Makes the input read-only when `true`. Maps to the `readonly` HTML attribute. */
  var readonly: Option[Boolean] = None

  /** Marks the input as required for form submission when `true`. Maps to the `required` HTML attribute. */
  var required: Option[Boolean] = None

  /** The maximum number of characters allowed. Maps to the `maxlength` HTML attribute. */
  var maxLength: Option[Int] = None

  /** The minimum number of characters required. Maps to the `minlength` HTML attribute. */
  var minLength: Option[Int] = None

  /** The minimum value for numeric or date inputs. Maps to the `min` HTML attribute. */
  var min: Option[String] = None

  /** The maximum value for numeric or date inputs. Maps to the `max` HTML attribute. */
  var max: Option[String] = None

  /** The stepping interval for numeric or date inputs. Maps to the `step` HTML attribute. */
  var step: Option[String] = None

  /** A regex pattern the input value must match. Maps to the `pattern` HTML attribute. */
  var pattern: Option[String] = None

  /** A comma-separated list of accepted file types (for `type="file"`). Maps to the `accept` HTML attribute. */
  var accept: Option[String] = None

  /** Allows multiple values or files to be selected when `true`. Maps to the `multiple` HTML attribute. */
  var multiple: Option[Boolean] = None

  /** Whether the checkbox or radio button is checked. Maps to the `checked` HTML attribute. */
  var checked: Option[Boolean] = None

  /** Browser autocomplete hint (e.g. `"on"`, `"off"`, `"email"`). Maps to the `autocomplete` HTML attribute. */
  var autocomplete: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

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

/** Props for components whose root element is `<a>` (anchor).
  *
  * Exposes anchor-specific HTML attributes:
  * `href`, `target`, `rel`, `download`, `hreflang`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/a MDN: a]]
  */
trait AnchorHtmlProps extends HtmlProps:
  /** The URL the link points to. Maps to the `href` HTML attribute. */
  var href: Option[String] = None

  /** Where to open the linked URL (e.g. `"_blank"`, `"_self"`). Maps to the `target` HTML attribute. */
  var target: Option[String] = None

  /** The relationship between the current document and the linked URL (e.g. `"noopener"`, `"noreferrer"`). Maps to the `rel` HTML attribute. */
  var rel: Option[String] = None

  /** Prompts a file download; the value becomes the suggested file name. Maps to the `download` HTML attribute. */
  var download: Option[String] = None

  /** The language of the linked resource (e.g. `"en"`, `"ja"`). Maps to the `hreflang` HTML attribute. */
  var hreflang: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

    addStr(b, "href", href)
    addStr(b, "target", target)
    addStr(b, "rel", rel)
    addStr(b, "download", download)
    addStr(b, "hreflang", hreflang)
    b ++= html.entries
    HtmlAttrs(b.result())

/** Props for components whose root element is `<form>`.
  *
  * Exposes form-specific HTML attributes:
  * `action`, `method`, `enctype`, `target` (via `formTarget`),
  * `novalidate`, `name`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/form MDN: form]]
  */
trait FormHtmlProps extends HtmlProps:
  /** The URL to which form data is sent on submission. Maps to the `action` HTML attribute. */
  var action: Option[String] = None

  /** The HTTP method used to submit the form (`"get"` or `"post"`). Maps to the `method` HTML attribute. */
  var method: Option[String] = None

  /** How the form data is encoded for submission (e.g. `"multipart/form-data"`). Maps to the `enctype` HTML attribute. */
  var enctype: Option[String] = None

  /** Where to display the response after submission (e.g. `"_blank"`, `"_self"`). Maps to the `target` HTML attribute. */
  var formTarget: Option[String] = None

  /** When `true`, bypasses native browser validation on submission. Maps to the `novalidate` HTML attribute. */
  var novalidate: Option[Boolean] = None

  /** The name of the form, used to reference it from JavaScript. Maps to the `name` HTML attribute. */
  var name: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

    addStr(b, "action", action)
    addStr(b, "method", method)
    addStr(b, "enctype", enctype)
    addStr(b, "target", formTarget)
    addBool(b, "novalidate", novalidate)
    addStr(b, "name", name)
    b ++= html.entries
    HtmlAttrs(b.result())

/** Props for components whose root element is `<img>`.
  *
  * Exposes image-specific HTML attributes:
  * `src`, `alt`, `width`, `height`, `loading`, `decoding`,
  * `srcset`, `sizes`, `crossorigin`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/img MDN: img]]
  */
trait ImgHtmlProps extends HtmlProps:
  /** The URL of the image. Maps to the `src` HTML attribute. */
  var src: Option[String] = None

  /** The alternative text description of the image, used for accessibility. Maps to the `alt` HTML attribute. */
  var alt: Option[String] = None

  /** The intrinsic width of the image in pixels. Maps to the `width` HTML attribute. */
  var width: Option[Int] = None

  /** The intrinsic height of the image in pixels. Maps to the `height` HTML attribute. */
  var height: Option[Int] = None

  /** How the browser should load the image (`"eager"` or `"lazy"`). Maps to the `loading` HTML attribute. */
  var loading: Option[String] = None

  /** How the browser should decode the image (`"sync"`, `"async"`, `"auto"`). Maps to the `decoding` HTML attribute. */
  var decoding: Option[String] = None

  /** A comma-separated list of image URLs and descriptors for responsive images. Maps to the `srcset` HTML attribute. */
  var srcset: Option[String] = None

  /** Media conditions that specify which srcset entry to use at different viewport widths. Maps to the `sizes` HTML attribute. */
  var sizes: Option[String] = None

  /** How the element handles cross-origin requests (`"anonymous"`, `"use-credentials"`). Maps to the `crossorigin` HTML attribute. */
  var crossorigin: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

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

/** Props for components whose root element is `<select>`.
  *
  * Exposes select-specific HTML attributes:
  * `name`, `disabled`, `required`, `multiple`, `size`, `form`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/select MDN: select]]
  */
trait SelectHtmlProps extends HtmlProps:
  /** The name of the select control, submitted as part of form data. Maps to the `name` HTML attribute. */
  var name: Option[String] = None

  /** Disables the select control when `true`. Maps to the `disabled` HTML attribute. */
  var disabled: Option[Boolean] = None

  /** Marks the select as required for form submission when `true`. Maps to the `required` HTML attribute. */
  var required: Option[Boolean] = None

  /** Allows multiple options to be selected simultaneously when `true`. Maps to the `multiple` HTML attribute. */
  var multiple: Option[Boolean] = None

  /** The number of visible options in the list box. Maps to the `size` HTML attribute. */
  var size: Option[Int] = None

  /** Associates the select with a `<form>` element by its `id`. Maps to the `form` HTML attribute. */
  var form: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

    addStr(b, "name", name)
    addBool(b, "disabled", disabled)
    addBool(b, "required", required)
    addBool(b, "multiple", multiple)
    addInt(b, "size", size)
    addStr(b, "form", form)
    b ++= html.entries
    HtmlAttrs(b.result())

/** Props for components whose root element is `<textarea>`.
  *
  * Exposes textarea-specific HTML attributes:
  * `name`, `placeholder`, `rows`, `cols`, `disabled`, `readonly`,
  * `required`, `maxlength`, `minlength`, `wrap`.
  *
  * @see [[https://developer.mozilla.org/en-US/docs/Web/HTML/Element/textarea MDN: textarea]]
  */
trait TextAreaHtmlProps extends HtmlProps:
  /** The name of the textarea, submitted as part of form data. Maps to the `name` HTML attribute. */
  var name: Option[String] = None

  /** Hint text displayed when the textarea is empty. Maps to the `placeholder` HTML attribute. */
  var placeholder: Option[String] = None

  /** The visible number of text lines. Maps to the `rows` HTML attribute. */
  var rows: Option[Int] = None

  /** The visible width in average character widths. Maps to the `cols` HTML attribute. */
  var cols: Option[Int] = None

  /** Disables the textarea when `true`. Maps to the `disabled` HTML attribute. */
  var disabled: Option[Boolean] = None

  /** Makes the textarea read-only when `true`. Maps to the `readonly` HTML attribute. */
  var readonly: Option[Boolean] = None

  /** Marks the textarea as required for form submission when `true`. Maps to the `required` HTML attribute. */
  var required: Option[Boolean] = None

  /** The maximum number of characters allowed. Maps to the `maxlength` HTML attribute. */
  var maxLength: Option[Int] = None

  /** The minimum number of characters required. Maps to the `minlength` HTML attribute. */
  var minLength: Option[Int] = None

  /** How the text is wrapped on form submission (`"soft"`, `"hard"`). Maps to the `wrap` HTML attribute. */
  var wrap: Option[String] = None

  override def allHtmlAttrs: HtmlAttrs =
    val b = Map.newBuilder[String, String]
    collectGlobalAttrs(b)

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
