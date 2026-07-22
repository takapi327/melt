/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.PropsCodec

// Top-level so `compileErrors` can name them. `address` is a nested case class
// with no FieldEncoder/FieldCodec instance — used for the no-encoder test below.
case class FieldByNameAddr(city: String, zip: String) derives PropsCodec
case class FieldByNameForm(email: String, age: Int, address: FieldByNameAddr) derives PropsCodec

/** Compile-time behaviour of the by-name field spread (`form.field("email")`).
  *
  * Value assertions differ per platform (Map on JVM, HtmlAttrs on JS) so they live
  * in the platform `FormSpreadSpec`s; the negative cases here are identical on both
  * platforms because they are driven by the shared `FormMacros.fieldAttrsImpl`.
  */
class FormFieldByNameSpec extends munit.FunSuite:

  // `compileErrors` needs a statically-known string literal, so the constructor is
  // spelled out in each case (no interpolation).

  test("field rejects a name with no matching field at compile time") {
    val errs = compileErrors(
      """melt.runtime.forms.Form(melt.runtime.forms.FieldByNameForm("", 0, melt.runtime.forms.FieldByNameAddr("", ""))).field("nope")"""
    )
    assert(errs.nonEmpty, "expected a compile error for an unknown field name")
  }

  test("field rejects a non-literal name at compile time") {
    val errs = compileErrors(
      """val n = "email"; melt.runtime.forms.Form(melt.runtime.forms.FieldByNameForm("", 0, melt.runtime.forms.FieldByNameAddr("", ""))).field(n)"""
    )
    assert(errs.nonEmpty, "expected a compile error for a non-literal name")
  }

  test("field rejects a field whose type has no FieldEncoder at compile time") {
    // `address` is a nested case class: no FieldEncoder is in scope for it.
    val errs = compileErrors(
      """melt.runtime.forms.Form(melt.runtime.forms.FieldByNameForm("", 0, melt.runtime.forms.FieldByNameAddr("", ""))).field("address")"""
    )
    assert(errs.nonEmpty, "expected a compile error for a field type with no encoder")
  }

  test("fieldValue is subject to the same field-existence check") {
    val errs = compileErrors(
      """melt.runtime.forms.Form(melt.runtime.forms.FieldByNameForm("", 0, melt.runtime.forms.FieldByNameAddr("", ""))).fieldValue("nope")"""
    )
    assert(errs.nonEmpty, "expected a compile error for an unknown field name")
  }
