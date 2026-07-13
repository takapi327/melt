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

  test("form.text (SSR) yields a name/value map derived from the selector") {
    val form = Form(LoginForm("a@b.com", "secret"))
    // name comes from the field label (compile-checked); value is the seed
    assertEquals(form.text(_.email), Map[String, Any]("name" -> "email", "value" -> "a@b.com"))
    assertEquals(form.text(_.password), Map[String, Any]("name" -> "password", "value" -> "secret"))
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

object FormSpreadSpec:
  case class SampleForm(email: String) derives PropsCodec
