/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** A container for HTML attributes to be forwarded to a component's root element.
  *
  * {{{
  * // In a component template:
  * <div {...props.html}>...</div>
  * }}}
  */
case class HtmlAttrs(entries: Map[String, String]):
  /** Applies all attributes to the given element. */
  def apply(el: dom.Element): Unit =
    entries.foreach { case (k, v) => el.setAttribute(k, v) }

object HtmlAttrs:
  val empty:                           HtmlAttrs = HtmlAttrs(Map.empty)
  def apply(pairs: (String, String)*): HtmlAttrs = HtmlAttrs(pairs.toMap)

/** Trait for component Props that support HTML attribute forwarding. */
trait HtmlProps:
  private var _html:              HtmlAttrs = HtmlAttrs.empty
  def html:                       HtmlAttrs = _html
  def withHtml(attrs: HtmlAttrs): this.type =
    _html = attrs
    this

/** Element-specific HtmlProps traits for type-safe attribute forwarding. */
trait ButtonHtmlProps   extends HtmlProps
trait InputHtmlProps    extends HtmlProps
trait AnchorHtmlProps   extends HtmlProps
trait FormHtmlProps     extends HtmlProps
trait ImgHtmlProps      extends HtmlProps
trait SelectHtmlProps   extends HtmlProps
trait TextAreaHtmlProps extends HtmlProps
