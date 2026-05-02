/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.{ BodyError, FormData }
import meltkit.codec.{ FormDataDecoder, FormFieldDecoder }

class FormDataDecoderTest extends munit.FunSuite:

  case class LoginForm(username: String, password: String) derives FormDataDecoder
  case class ItemForm(name: String, count: Int) derives FormDataDecoder
  case class CheckboxForm(name: String, agree: Boolean) derives FormDataDecoder
  case class OptionalForm(name: String, bio: Option[String]) derives FormDataDecoder
  case class ListForm(name: String, tags: List[String]) derives FormDataDecoder

  // ── derived: basic ────────────────────────────────────────────────────────

  test("decodes all fields present"):
    val result = FormDataDecoder[LoginForm].decode(
      FormData.parse("username=alice&password=secret").toOption.get
    )
    assertEquals(result, Right(LoginForm("alice", "secret")))

  test("returns ValidationError for missing required field"):
    val result = FormDataDecoder[LoginForm].decode(
      FormData.parse("username=alice").toOption.get
    )
    assert(result.isLeft)
    result.left.toOption.get match
      case BodyError.ValidationError(errors) =>
        assert(errors.exists(_.contains("password")))
      case other => fail(s"Expected ValidationError, got $other")

  test("accumulates multiple errors"):
    val result = FormDataDecoder[LoginForm].decode(FormData.empty)
    assert(result.isLeft)
    result.left.toOption.get match
      case BodyError.ValidationError(errors) =>
        assert(errors.size >= 2)
        assert(errors.exists(_.contains("username")))
        assert(errors.exists(_.contains("password")))
      case other => fail(s"Expected ValidationError, got $other")

  // ── derived: Int ──────────────────────────────────────────────────────────

  test("decodes Int field"):
    val result = FormDataDecoder[ItemForm].decode(
      FormData.parse("name=item&count=3").toOption.get
    )
    assertEquals(result, Right(ItemForm("item", 3)))

  test("returns error for invalid Int"):
    val result = FormDataDecoder[ItemForm].decode(
      FormData.parse("name=item&count=abc").toOption.get
    )
    assert(result.isLeft)
    result.left.toOption.get match
      case BodyError.ValidationError(errors) =>
        assert(errors.exists(_.contains("count")))
      case other => fail(s"Expected ValidationError, got $other")

  // ── derived: Boolean ──────────────────────────────────────────────────────

  test("decodes Boolean field with 'on' value"):
    val result = FormDataDecoder[CheckboxForm].decode(
      FormData.parse("name=alice&agree=on").toOption.get
    )
    assertEquals(result, Right(CheckboxForm("alice", true)))

  test("absent Boolean field defaults to false"):
    val result = FormDataDecoder[CheckboxForm].decode(
      FormData.parse("name=alice").toOption.get
    )
    assertEquals(result, Right(CheckboxForm("alice", false)))

  // ── derived: Option ───────────────────────────────────────────────────────

  test("absent Optional field decodes as None"):
    val result = FormDataDecoder[OptionalForm].decode(
      FormData.parse("name=alice").toOption.get
    )
    assertEquals(result, Right(OptionalForm("alice", None)))

  test("present Optional field decodes as Some"):
    val result = FormDataDecoder[OptionalForm].decode(
      FormData.parse("name=alice&bio=hello").toOption.get
    )
    assertEquals(result, Right(OptionalForm("alice", Some("hello"))))

  // ── derived: List ─────────────────────────────────────────────────────────

  test("decodes List field with multiple values"):
    val result = FormDataDecoder[ListForm].decode(
      FormData.parse("name=alice&tags=a&tags=b").toOption.get
    )
    assertEquals(result, Right(ListForm("alice", List("a", "b"))))

  test("absent List field decodes as empty list"):
    val result = FormDataDecoder[ListForm].decode(
      FormData.parse("name=alice").toOption.get
    )
    assertEquals(result, Right(ListForm("alice", List.empty)))

  // ── BodyDecoder bridge ────────────────────────────────────────────────────

  test("BodyDecoder[A] bridge works via FormDataDecoder"):
    import meltkit.codec.FormDataDecoder.given
    val decoder = summon[meltkit.codec.BodyDecoder[LoginForm]]
    val result  = decoder.decode("username=bob&password=pass123")
    assertEquals(result, Right(LoginForm("bob", "pass123")))

  test("BodyDecoder[A] bridge returns error for invalid body"):
    import meltkit.codec.FormDataDecoder.given
    val decoder = summon[meltkit.codec.BodyDecoder[LoginForm]]
    val result  = decoder.decode("username=bob")
    assert(result.isLeft)
