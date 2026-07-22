/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

/** Builds the attribute maps for the `form.*` control spread helpers, shared by
  * the client (`HtmlAttrs`) and SSR (`Map[String, Any]`) sides.
  *
  * Boolean HTML attributes (`checked`, `selected`) are represented by presence:
  * the key is included with an empty value when on, and omitted when off, so the
  * spread mechanism (which only sets attributes) toggles them correctly.
  */
private[forms] object ControlAttrs:

  def checkbox(name: String, checked: Boolean): Map[String, Any] =
    // value="true" so a checked box submits `name=true`, decodable as Boolean
    val base = Map[String, Any]("name" -> name, "type" -> "checkbox", "value" -> "true")
    if checked then base + ("checked" -> "") else base

  def radio(name: String, value: String, checked: Boolean): Map[String, Any] =
    val base = Map[String, Any]("name" -> name, "type" -> "radio", "value" -> value)
    if checked then base + ("checked" -> "") else base

  def select(name: String): Map[String, Any] =
    Map[String, Any]("name" -> name)

  def option(value: String, selected: Boolean): Map[String, Any] =
    val base = Map[String, Any]("value" -> value)
    if selected then base + ("selected" -> "") else base

  /** State-only `checked` for auto-binding, where the user already wrote `name`
    * and `type` (so only the reflected state is injected). A plain checkbox with no
    * `value` submits `on` when checked, which the Boolean codec decodes as `true`.
    */
  def checkedState(checked: Boolean): Map[String, Any] =
    if checked then Map[String, Any]("checked" -> "") else Map.empty

  /** State-only `selected` for auto-binding an `<option>`. */
  def selectedState(selected: Boolean): Map[String, Any] =
    if selected then Map[String, Any]("selected" -> "") else Map.empty
