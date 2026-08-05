/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms.codec

/** Tests for the query-oriented [[FieldDecoder]] additions: `Set` from repeated
  * values and `spaceDelimited` from a single whitespace-separated value (OIDC
  * `scope` / `response_type` / `prompt` / `acr_values`).
  */
class FieldDecoderSpec extends munit.FunSuite:

  test("Set decoder collects repeated values and dedupes"):
    assertEquals(FieldDecoder[Set[Int]].decode("id", List("1", "2", "2", "3")), Right(Set(1, 2, 3)))

  test("Set decoder: absent field decodes to the empty set"):
    assertEquals(FieldDecoder[Set[String]].decode("tag", Nil), Right(Set.empty[String]))

  test("Set decoder propagates an element decode error"):
    assert(FieldDecoder[Set[Int]].decode("id", List("1", "x")).isLeft)

  test("spaceDelimited splits a single value and decodes each element"):
    val d = FieldDecoder.spaceDelimited[String]
    assertEquals(d.decode("scope", List("openid profile email")), Right(Set("openid", "profile", "email")))

  test("spaceDelimited: absent decodes to the empty set"):
    assertEquals(FieldDecoder.spaceDelimited[String].decode("scope", Nil), Right(Set.empty[String]))

  test("spaceDelimited tolerates surrounding and repeated whitespace"):
    assertEquals(FieldDecoder.spaceDelimited[String].decode("s", List("  a   b  ")), Right(Set("a", "b")))

  test("spaceDelimited propagates an element decode error"):
    assert(FieldDecoder.spaceDelimited[Int].decode("n", List("1 x 3")).isLeft)

  test("spaceDelimited composes with emap for domain types"):
    val d = FieldDecoder.spaceDelimited[String].emap(set => Right(set.map(_.toUpperCase)))
    assertEquals(d.decode("s", List("a b")), Right(Set("A", "B")))

  // ── Option wrapping at the FieldDecoder level (decode-only) ────────────────
  // FieldCodec[Option[A]] already covers codec-backed types; this low-priority
  // given lets a decode-only FieldDecoder (e.g. spaceDelimited) be wrapped in
  // Option too, so `Option[Set[Scope]]` (OIDC token `scope`) can be decoded.

  test("Option wraps a decode-only FieldDecoder (spaceDelimited): absent -> None"):
    given FieldDecoder[Set[String]] = FieldDecoder.spaceDelimited[String]
    assertEquals(FieldDecoder[Option[Set[String]]].decode("scope", Nil), Right(None))
    assertEquals(
      FieldDecoder[Option[Set[String]]].decode("scope", List("openid profile")),
      Right(Some(Set("openid", "profile")))
    )

  test("Option over a codec-backed scalar still works (no ambiguity)"):
    assertEquals(FieldDecoder[Option[Int]].decode("n", Nil), Right(None))
    assertEquals(FieldDecoder[Option[Int]].decode("n", List("42")), Right(Some(42)))
    assert(FieldDecoder[Option[Int]].decode("n", List("x")).isLeft)
