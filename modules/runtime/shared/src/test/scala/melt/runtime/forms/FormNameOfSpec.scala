/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.PropsCodec

// Top-level so `compileErrors` can reference it in the negative test below.
case class NameOfForm(email: String, password: String, errors: List[String] = Nil) derives PropsCodec

class FormNameOfSpec extends munit.FunSuite:

  private val form = Form(NameOfForm("", ""))

  test("nameOf derives the field label from a selector") {
    assertEquals(form.nameOf(_.email), "email")
    assertEquals(form.nameOf(_.password), "password")
    assertEquals(form.nameOf(_.errors), "errors")
  }

  test("nameOf yields exactly the case-class field name FormDataDecoder reads") {
    // The decoder reads FormData.get(fieldName); nameOf returns the same label,
    // so the input name and the decoded field can never drift apart.
    NameOfForm("", "").productElementNames.toList.foreach { field =>
      assert(Set("email", "password", "errors").contains(field), field)
    }
    assertEquals(form.nameOf(_.email), "email")
  }

  test("nameOf rejects a chained (non-field) selector at compile time") {
    val errs = compileErrors("""melt.runtime.forms.Form(melt.runtime.forms.NameOfForm("", "")).nameOf(_.email.trim)""")
    assert(errs.nonEmpty, "expected a compile error for a non-field selector")
  }
