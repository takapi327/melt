/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.PropsCodec

class FormSpreadSpec extends munit.FunSuite:

  case class LoginForm(email: String, password: String) derives PropsCodec

  test("form.text (SPA) yields HtmlAttrs with name/value derived from the selector") {
    val form = Form(LoginForm("a@b.com", "secret"))
    assertEquals(form.text(_.email).entries, Map("name" -> "email", "value" -> "a@b.com"))
    assertEquals(form.text(_.password).entries, Map("name" -> "password", "value" -> "secret"))
  }
