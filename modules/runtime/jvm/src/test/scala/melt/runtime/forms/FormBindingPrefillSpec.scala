/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.PropsCodec
import melt.runtime.render.ServerRenderer

/** End-to-end check that the code `FormBindingPass` emits for a `use:form` input
  * composes with the runtime to produce a prefilled `<input>` on the server.
  *
  * `FormBindingPass` injects `IrAttr.Spread(form.fieldValue("email"))`, which the
  * SSR emitter turns into `renderer.spreadAttrs("input", form.fieldValue("email"))`.
  * The codegen tests assert that exact text is emitted; this test runs the emitted
  * shape against a real [[ServerRenderer]] to prove the value is actually baked in.
  */
class FormBindingPrefillSpec extends munit.FunSuite:

  case class LoginForm(email: String, password: String, remember: Boolean = false, role: String = "user")
    derives PropsCodec

  /** Reproduces the SSR emitter's output for `<input name="email" type="email"/>`
    * under `use:form={form}`: the static attrs are pushed, then the injected spread. */
  private def renderInput(form: Form[LoginForm], tag: String, staticAttrs: String, spread: Map[String, Any]): String =
    val r = ServerRenderer()
    r.push(s"<$tag$staticAttrs")
    r.spreadAttrs(tag, spread)
    r.push("/>")
    r.result().body

  test("text input: fieldValue seeds the value attribute from the form field") {
    val form = Form(LoginForm("a@b.com", "secret"))
    val html = renderInput(form, "input", """ name="email" type="email"""", form.fieldValue("email"))
    assertEquals(html, """<input name="email" type="email" value="a@b.com"/>""")
  }

  test("checkbox: checkedState emits `checked` only when the field is true") {
    val on  = Form(LoginForm("a@b.com", "x", remember = true))
    val off = Form(LoginForm("a@b.com", "x", remember = false))
    assertEquals(
      renderInput(on, "input", """ name="remember" type="checkbox"""", on.checkedState("remember")),
      """<input name="remember" type="checkbox" checked=""/>"""
    )
    assertEquals(
      renderInput(off, "input", """ name="remember" type="checkbox"""", off.checkedState("remember")),
      """<input name="remember" type="checkbox"/>"""
    )
  }

  test("option: optionState marks `selected` on the option matching the field") {
    val form = Form(LoginForm("a@b.com", "x", role = "admin"))
    assertEquals(
      renderInput(form, "option", """ value="admin"""", form.optionState("role", "admin")),
      """<option value="admin" selected=""/>"""
    )
    assertEquals(
      renderInput(form, "option", """ value="user"""", form.optionState("role", "user")),
      """<option value="user"/>"""
    )
  }

  test("textarea: fieldText yields the content string (escaped when rendered as a text node)") {
    val form = Form(LoginForm("a@b.com <script>", "x"))
    val r    = ServerRenderer()
    r.push("<textarea name=\"email\">")
    r.push(melt.runtime.Escape.html(form.fieldText("email"))) // mirrors how a text node is emitted
    r.push("</textarea>")
    assertEquals(r.result().body, "<textarea name=\"email\">a@b.com &lt;script&gt;</textarea>")
  }
