/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.{ PropsCodec, SimpleJson }

class FormSpec extends munit.FunSuite:

  case class LoginForm(email: String, errors: List[String]) derives PropsCodec

  test("Form seeds data from the initial value and starts not submitting") {
    val form = Form(LoginForm("a@b.com", Nil))
    assertEquals(form.data.value, LoginForm("a@b.com", Nil))
    assertEquals(form.submitting.value, false)
  }

  test("applyResultData decodes the envelope data field and updates the reactive state") {
    val form = Form(LoginForm("", Nil))
    form.applyResultData(SimpleJson.parse("""{"email":"x@y.com","errors":["bad"]}"""))
    assertEquals(form.data.value, LoginForm("x@y.com", List("bad")))
  }

  test("data is reactive: subscribers see updates from applyResultData") {
    val form     = Form(LoginForm("", Nil))
    var observed = List.empty[String]
    val cancel   = form.data.subscribe(f => observed = f.email :: observed)
    form.applyResultData(SimpleJson.parse("""{"email":"one@x.com","errors":[]}"""))
    form.applyResultData(SimpleJson.parse("""{"email":"two@x.com","errors":[]}"""))
    cancel()
    assertEquals(observed.take(2), List("two@x.com", "one@x.com"))
  }

  test("submitting can be toggled reactively") {
    val form = Form(LoginForm("", Nil))
    form.submitting.set(true)
    assertEquals(form.submitting.value, true)
  }
