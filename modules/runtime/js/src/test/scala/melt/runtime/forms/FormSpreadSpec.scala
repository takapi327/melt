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

  test("form.text (SPA) yields HtmlAttrs with name/value derived from the selector") {
    val form = Form(LoginForm("a@b.com", "secret"))
    assertEquals(form.text(_.email).entries, Map("name" -> "email", "value" -> "a@b.com"))
    assertEquals(form.text(_.password).entries, Map("name" -> "password", "value" -> "secret"))
  }

  test("form.field (SPA) yields HtmlAttrs name/value from a literal field name") {
    val form = Form(LoginForm("a@b.com", "secret"))
    assertEquals(form.field("email").entries, Map("name" -> "email", "value" -> "a@b.com"))
    // fieldValue omits `name` (the user already wrote name="password")
    assertEquals(form.fieldValue("password").entries, Map("value" -> "secret"))
  }

  test("form.field (SPA) resolves a dotted nested path") {
    val form = Form(Profile("kit", Addr("Tokyo", "100-0001")))
    assertEquals(form.field("addr.city").entries, Map("name" -> "addr.city", "value" -> "Tokyo"))
    assertEquals(form.fieldValue("addr.zip").entries, Map("value" -> "100-0001"))
  }

  test("form.checkboxField / radioField / selectField / optionField (by name)") {
    val prefs = Form(Prefs(true, "admin"))
    assertEquals(
      prefs.checkboxField("remember").entries,
      Map("name" -> "remember", "type" -> "checkbox", "value" -> "true", "checked" -> "")
    )
    assertEquals(
      prefs.radioField("role", "admin").entries,
      Map("name" -> "role", "type" -> "radio", "value" -> "admin", "checked" -> "")
    )
    assertEquals(prefs.selectField("role").entries, Map("name" -> "role"))
    assertEquals(prefs.optionField("role", "admin").entries, Map("value" -> "admin", "selected" -> ""))
    assertEquals(prefs.optionField("role", "user").entries, Map("value" -> "user"))
  }
