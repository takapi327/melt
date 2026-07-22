/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.PropsCodec

class FormSpreadSpec extends munit.FunSuite:

  case class LoginForm(email: String, password: String) derives PropsCodec
  case class Prefs(remember: Boolean, role: String) derives PropsCodec
  case class Addr(city: String, zip: String) derives PropsCodec
  case class Profile(name: String, addr: Addr) derives PropsCodec

  test("form.text (SSR) yields a name/value map derived from the selector") {
    val form = Form(LoginForm("a@b.com", "secret"))
    // name comes from the field label (compile-checked); value is the seed
    assertEquals(form.text(_.email), Map[String, Any]("name" -> "email", "value" -> "a@b.com"))
    assertEquals(form.text(_.password), Map[String, Any]("name" -> "password", "value" -> "secret"))
  }

  test("form.field (SSR) resolves name + seeded value from a literal field name") {
    val form = Form(LoginForm("a@b.com", "secret"))
    assertEquals(form.field("email"), Map[String, Any]("name" -> "email", "value" -> "a@b.com"))
    // fieldValue omits `name` (the user already wrote name="password")
    assertEquals(form.fieldValue("password"), Map[String, Any]("value" -> "secret"))
  }

  test("form.field resolves a dotted nested path") {
    val form = Form(Profile("kit", Addr("Tokyo", "100-0001")))
    assertEquals(form.field("addr.city"), Map[String, Any]("name" -> "addr.city", "value" -> "Tokyo"))
    assertEquals(form.fieldValue("addr.zip"), Map[String, Any]("value" -> "100-0001"))
  }

  test("form.text rejects a chained (non-field) selector at compile time") {
    val errs = compileErrors(
      """melt.runtime.forms.Form(melt.runtime.forms.FormSpreadSpec.SampleForm("")).text(_.email.trim)"""
    )
    assert(errs.nonEmpty, "expected a compile error for a non-field selector")
  }

  test("form.checkbox: name/type/value, with `checked` only when the field is true") {
    assertEquals(
      Form(Prefs(true, "admin")).checkbox(_.remember),
      Map[String, Any]("name" -> "remember", "type" -> "checkbox", "value" -> "true", "checked" -> "")
    )
    assertEquals(
      Form(Prefs(false, "admin")).checkbox(_.remember),
      Map[String, Any]("name" -> "remember", "type" -> "checkbox", "value" -> "true")
    )
  }

  test("form.radio: value + `checked` when the field equals this option") {
    val form = Form(Prefs(true, "admin"))
    assertEquals(
      form.radio(_.role, "admin"),
      Map[String, Any]("name" -> "role", "type" -> "radio", "value" -> "admin", "checked" -> "")
    )
    assertEquals(
      form.radio(_.role, "user"),
      Map[String, Any]("name" -> "role", "type" -> "radio", "value" -> "user")
    )
  }

  test("form.select + form.option: select sets name; option sets value + `selected` on match") {
    val form = Form(Prefs(true, "admin"))
    assertEquals(form.select(_.role), Map[String, Any]("name" -> "role"))
    assertEquals(form.option(_.role, "admin"), Map[String, Any]("value" -> "admin", "selected" -> ""))
    assertEquals(form.option(_.role, "user"), Map[String, Any]("value" -> "user"))
  }

  test("form.checkboxField (by name): matches the selector form for a Boolean field") {
    assertEquals(
      Form(Prefs(true, "admin")).checkboxField("remember"),
      Map[String, Any]("name" -> "remember", "type" -> "checkbox", "value" -> "true", "checked" -> "")
    )
    assertEquals(
      Form(Prefs(false, "admin")).checkboxField("remember"),
      Map[String, Any]("name" -> "remember", "type" -> "checkbox", "value" -> "true")
    )
  }

  test("form.checkboxField rejects a non-Boolean field at compile time") {
    val errs = compileErrors(
      """melt.runtime.forms.Form(melt.runtime.forms.FormSpreadSpec.SampleForm("")).checkboxField("email")"""
    )
    assert(errs.nonEmpty, "expected a compile error for a non-Boolean checkbox field")
  }

  test("form.radioField (by name): value + `checked` when the field equals this option") {
    val form = Form(Prefs(true, "admin"))
    assertEquals(
      form.radioField("role", "admin"),
      Map[String, Any]("name" -> "role", "type" -> "radio", "value" -> "admin", "checked" -> "")
    )
    assertEquals(
      form.radioField("role", "user"),
      Map[String, Any]("name" -> "role", "type" -> "radio", "value" -> "user")
    )
  }

  test("form.selectField + form.optionField (by name): mirror the selector forms") {
    val form = Form(Prefs(true, "admin"))
    assertEquals(form.selectField("role"), Map[String, Any]("name" -> "role"))
    assertEquals(form.optionField("role", "admin"), Map[String, Any]("value" -> "admin", "selected" -> ""))
    assertEquals(form.optionField("role", "user"), Map[String, Any]("value" -> "user"))
  }

  test("form.checkedState / radioState (auto-binding): state-only, no name/type/value") {
    assertEquals(Form(Prefs(true, "admin")).checkedState("remember"), Map[String, Any]("checked" -> ""))
    assertEquals(Form(Prefs(false, "admin")).checkedState("remember"), Map.empty[String, Any])
    assertEquals(Form(Prefs(true, "admin")).radioState("role", "admin"), Map[String, Any]("checked" -> ""))
    assertEquals(Form(Prefs(true, "admin")).radioState("role", "user"), Map.empty[String, Any])
  }

  test("form.optionState (auto-binding): `selected` only, when the field equals this option") {
    assertEquals(Form(Prefs(true, "admin")).optionState("role", "admin"), Map[String, Any]("selected" -> ""))
    assertEquals(Form(Prefs(true, "admin")).optionState("role", "user"), Map.empty[String, Any])
  }

  test("form.fieldText (auto-binding): the field's wire value, for textarea content") {
    assertEquals(Form(LoginForm("a@b.com", "secret")).fieldText("email"), "a@b.com")
  }

object FormSpreadSpec:
  case class SampleForm(email: String) derives PropsCodec
