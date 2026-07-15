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
  // A form model carrying a server-populated per-field issues map.
  case class IssueForm(title: String, errors: Map[String, List[String]] = Map.empty) derives FormDataDecoder

  case class Address(city: String, zip: String) derives FormDataDecoder
  case class User(name: String, address: Address) derives FormDataDecoder
  case class Company(name: String, hq: Address) derives FormDataDecoder

  // ── derived: basic ────────────────────────────────────────────────────────

  test("decodes all fields present"):
    val result = FormDataDecoder[LoginForm].decode(
      FormData.parse("username=alice&password=secret").toOption.get
    )
    assertEquals(result, Right(LoginForm("alice", "secret")))

  test("a per-field issues map field derives and decodes to empty from a submission"):
    val result = FormDataDecoder[IssueForm].decode(FormData.parse("title=hello").toOption.get)
    assertEquals(result, Right(IssueForm("hello", Map.empty)))

  test("returns FieldErrors keyed by the missing required field"):
    val result = FormDataDecoder[LoginForm].decode(
      FormData.parse("username=alice").toOption.get
    )
    assert(result.isLeft)
    result.left.toOption.get match
      case BodyError.FieldErrors(byField) =>
        assert(byField.contains("password"), byField.toString)
        assert(!byField.contains("username"), byField.toString)
      case other => fail(s"Expected FieldErrors, got $other")

  test("accumulates errors for every failing field"):
    val result = FormDataDecoder[LoginForm].decode(FormData.empty)
    assert(result.isLeft)
    result.left.toOption.get match
      case BodyError.FieldErrors(byField) =>
        assert(byField.contains("username"), byField.toString)
        assert(byField.contains("password"), byField.toString)
      case other => fail(s"Expected FieldErrors, got $other")

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
      case BodyError.FieldErrors(byField) =>
        assert(byField.contains("count"), byField.toString)
      case other => fail(s"Expected FieldErrors, got $other")

  // ── derived: Boolean ──────────────────────────────────────────────────────

  test("decodes Boolean field with 'true' value"):
    val result = FormDataDecoder[CheckboxForm].decode(
      FormData.parse("name=alice&agree=true").toOption.get
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

  // ── nested case classes (hierarchical keys) ───────────────────────────────

  test("decodes a nested case class from `field.subfield` keys"):
    val result = FormDataDecoder[User].decode(
      FormData.parse("name=alice&address.city=Tokyo&address.zip=100-0001").toOption.get
    )
    assertEquals(result, Right(User("alice", Address("Tokyo", "100-0001"))))

  test("a missing nested field errors with the prefixed path"):
    val result = FormDataDecoder[User].decode(
      FormData.parse("name=alice&address.city=Tokyo").toOption.get
    )
    assert(result.isLeft)
    result.left.toOption.get match
      case BodyError.FieldErrors(byField) =>
        // the nested failure is keyed under the parent field, message carries the path
        assert(byField.contains("address"), byField.toString)
        assert(byField("address").exists(e => e.contains("address") && e.contains("zip")), byField.toString)
      case other => fail(s"Expected FieldErrors, got $other")

  test("decodes a differently-named nested field"):
    val result = FormDataDecoder[Company].decode(
      FormData.parse("name=Acme&hq.city=Tokyo&hq.zip=100-0001").toOption.get
    )
    assertEquals(result, Right(Company("Acme", Address("Tokyo", "100-0001"))))
