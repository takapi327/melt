/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms.codec

// A domain type with a custom codec, used to prove decode/encode symmetry.
case class Email(value: String)
object Email:
  def parse(s: String): Either[String, Email] =
    if s.contains("@") then Right(Email(s)) else Left(s"invalid email: $s")

class FieldCodecSpec extends munit.FunSuite:

  test("String codec: decode reads first value, encode wraps it"):
    val c = FieldCodec[String]
    assertEquals(c.decode("email", List("a@b.com")), Right("a@b.com"))
    assertEquals(c.decode("email", Nil), Left("Missing required field: email"))
    assertEquals(c.encodeValue("a@b.com"), "a@b.com")

  test("Int codec: parses and renders, errors on malformed"):
    assertEquals(FieldCodec[Int].decode("age", List("42")), Right(42))
    assert(FieldCodec[Int].decode("age", List("x")).isLeft)
    assertEquals(FieldCodec[Int].encodeValue(42), "42")

  test("Boolean codec: absent decodes to false"):
    assertEquals(FieldCodec[Boolean].decode("remember", Nil), Right(false))
    assertEquals(FieldCodec[Boolean].decode("remember", List("true")), Right(true))
    assertEquals(FieldCodec[Boolean].encodeValue(true), "true")

  test("Option codec: absent = None, present = Some"):
    assertEquals(FieldCodec[Option[String]].decode("nick", Nil), Right(None))
    assertEquals(FieldCodec[Option[String]].decode("nick", List("bob")), Right(Some("bob")))
    assertEquals(FieldCodec[Option[String]].encode(None), Nil)
    assertEquals(FieldCodec[Option[String]].encode(Some("bob")), List("bob"))

  test("List codec: decodes every value and encodes each"):
    assertEquals(FieldCodec[List[Int]].decode("n", List("1", "2")), Right(List(1, 2)))
    assertEquals(FieldCodec[List[Int]].encode(List(1, 2)), List("1", "2"))

  test("a custom FieldCodec via eimap round-trips decode/encode"):
    given FieldCodec[Email] = FieldCodec[String].eimap(Email.parse)(_.value)
    val c = FieldCodec[Email]
    assertEquals(c.decode("email", List("a@b.com")), Right(Email("a@b.com")))
    assert(c.decode("email", List("nope")).isLeft)
    // symmetry: what encode renders decodes back to the same value
    val e = Email("a@b.com")
    assertEquals(c.decode("email", c.encode(e)), Right(e))
    assertEquals(c.encodeValue(e), "a@b.com")
