/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import munit.FunSuite

/** Phase A tests for [[AttrNameValidator]] — §12.1.2 attribute name validation. */
class AttrNameValidatorSpec extends FunSuite:

  test("ASCII alphanumerics are valid") {
    assert(AttrNameValidator.isValid("class"))
    assert(AttrNameValidator.isValid("data-id"))
    assert(AttrNameValidator.isValid("aria-label"))
  }

  test("Hyphens, colons, dots and underscores are allowed") {
    assert(AttrNameValidator.isValid("xlink:href"))
    assert(AttrNameValidator.isValid("my-custom-attr"))
    assert(AttrNameValidator.isValid("foo.bar"))
    assert(AttrNameValidator.isValid("foo_bar"))
  }

  test("Mixed case is allowed") {
    assert(AttrNameValidator.isValid("onClick"))
  }

  test("Whitespace-containing names are invalid") {
    assert(!AttrNameValidator.isValid("class onclick"))
    assert(!AttrNameValidator.isValid("class\tonclick"))
    assert(!AttrNameValidator.isValid("class\nonclick"))
  }

  test("Quote characters are invalid") {
    assert(!AttrNameValidator.isValid("""class"onclick"""))
    assert(!AttrNameValidator.isValid("class'onclick"))
  }

  test("Control markers (=, >, /) are invalid") {
    assert(!AttrNameValidator.isValid("class=onclick"))
    assert(!AttrNameValidator.isValid("class>onclick"))
    assert(!AttrNameValidator.isValid("class/onclick"))
  }

  test("Empty string is invalid") {
    assert(!AttrNameValidator.isValid(""))
  }

  test("Unicode noncharacters are invalid") {
    assert(!AttrNameValidator.isValid("foo\uFDD0"))
    assert(!AttrNameValidator.isValid("foo\uFFFE"))
    assert(!AttrNameValidator.isValid("foo\uFFFF"))
  }

  test("Multilingual letters are allowed") {
    assert(AttrNameValidator.isValid("data-名前"))
    assert(AttrNameValidator.isValid("データ"))
  }
