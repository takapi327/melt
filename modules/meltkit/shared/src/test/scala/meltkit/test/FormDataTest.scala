/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.{ BodyError, FormData }

class FormDataTest extends munit.FunSuite:

  // ── FormData.parse ────────────────────────────────────────────────────────

  test("parses basic key=value pairs"):
    val result = FormData.parse("name=Alice&age=30")
    assert(result.isRight)
    val form = result.toOption.get
    assertEquals(form.get("name"), Some("Alice"))
    assertEquals(form.get("age"), Some("30"))

  test("parses multiple values for the same key"):
    val form = FormData.parse("tag=a&tag=b&tag=c").toOption.get
    assertEquals(form.getAll("tag"), List("a", "b", "c"))

  test("decodes + as space and percent-encoded UTF-8"):
    val form = FormData.parse("name=hello+world&q=%E6%97%A5%E6%9C%AC").toOption.get
    assertEquals(form.get("name"), Some("hello world"))
    assertEquals(form.get("q"), Some("\u65E5\u672C"))

  test("preserves empty values"):
    val form = FormData.parse("name=&empty").toOption.get
    assertEquals(form.get("name"), Some(""))
    assertEquals(form.get("empty"), Some(""))

  test("empty string returns FormData.empty"):
    assertEquals(FormData.parse(""), Right(FormData.empty))

  test("blank string returns FormData.empty"):
    assertEquals(FormData.parse("   "), Right(FormData.empty))

  test("decodes special characters"):
    val form = FormData.parse("data=%26%3D%25").toOption.get
    assertEquals(form.get("data"), Some("&=%"))

  test("preserves = in values"):
    val form = FormData.parse("token=abc==def").toOption.get
    assertEquals(form.get("token"), Some("abc==def"))

  // ── FormData accessors ────────────────────────────────────────────────────

  test("get returns first value for duplicate keys"):
    val form = FormData.parse("a=1&a=2").toOption.get
    assertEquals(form.get("a"), Some("1"))

  test("getAll returns all values for duplicate keys"):
    val form = FormData.parse("a=1&a=2").toOption.get
    assertEquals(form.getAll("a"), List("1", "2"))

  test("getAll returns empty list for missing key"):
    val form = FormData.parse("a=1").toOption.get
    assertEquals(form.getAll("b"), List.empty)

  test("require returns Right for existing field"):
    val form = FormData.parse("name=Alice").toOption.get
    assertEquals(form.require("name"), Right("Alice"))

  test("require returns Left for missing field"):
    val form = FormData.parse("name=Alice").toOption.get
    assert(form.require("age").isLeft)
    assert(form.require("age").left.toOption.get.message.contains("age"))

  test("has returns true for existing field"):
    val form = FormData.parse("name=Alice").toOption.get
    assert(form.has("name"))

  test("has returns false for missing field"):
    val form = FormData.parse("name=Alice").toOption.get
    assert(!form.has("age"))

  test("toMap returns first value for each key"):
    val form = FormData.parse("a=1&a=2&b=3").toOption.get
    assertEquals(form.toMap, Map("a" -> "1", "b" -> "3"))

  // ── BodyDecoder[FormData] ─────────────────────────────────────────────────

  test("BodyDecoder[FormData] given is available without import"):
    val decoder = summon[meltkit.codec.BodyDecoder[FormData]]
    val result  = decoder.decode("x=1&y=2")
    assert(result.isRight)
    assertEquals(result.toOption.get.get("x"), Some("1"))
