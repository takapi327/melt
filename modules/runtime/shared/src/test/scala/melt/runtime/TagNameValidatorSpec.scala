/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import munit.FunSuite

/** Phase A tests for [[TagNameValidator]] / [[ComponentNameValidator]] —
  * §12.1.3 tag and component-name validation.
  */
class TagNameValidatorSpec extends FunSuite:

  test("Standard HTML tags are valid") {
    assert(TagNameValidator.isValid("div"))
    assert(TagNameValidator.isValid("h1"))
    assert(TagNameValidator.isValid("span"))
    assert(TagNameValidator.isValid("blockquote"))
  }

  test("Custom element names (with hyphen) are valid") {
    assert(TagNameValidator.isValid("my-element"))
    assert(TagNameValidator.isValid("x-foo"))
    assert(TagNameValidator.isValid("my-multi-word-name"))
  }

  test("Empty string is invalid") {
    assert(!TagNameValidator.isValid(""))
  }

  test("Tag starting with a digit is invalid") {
    assert(!TagNameValidator.isValid("1div"))
  }

  test("Tag starting with a hyphen is invalid") {
    assert(!TagNameValidator.isValid("-div"))
  }

  test("Colons are disallowed (melt:head is pre-processed into its own AST node)") {
    assert(!TagNameValidator.isValid("melt:head"))
    assert(!TagNameValidator.isValid("ns:tag"))
  }

  test("Whitespace is invalid") {
    assert(!TagNameValidator.isValid("div span"))
  }

  test("Quote / close-tag injection is invalid") {
    assert(!TagNameValidator.isValid("""div"onclick="alert(1)""""))
    assert(!TagNameValidator.isValid("div>"))
  }

class ComponentNameValidatorSpec extends FunSuite:

  test("PascalCase is valid") {
    assert(ComponentNameValidator.isValid("Counter"))
    assert(ComponentNameValidator.isValid("TodoList"))
    assert(ComponentNameValidator.isValid("App"))
  }

  test("Lowercase start is invalid") {
    assert(!ComponentNameValidator.isValid("counter"))
  }

  test("Digit start is invalid") {
    assert(!ComponentNameValidator.isValid("1Counter"))
  }

  test("Hyphens are invalid in Scala identifiers") {
    assert(!ComponentNameValidator.isValid("Todo-List"))
  }
