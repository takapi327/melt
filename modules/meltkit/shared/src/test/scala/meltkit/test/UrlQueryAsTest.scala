/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.forms.codec.FieldDecoder

import meltkit.Url

/** Tests for typed query decoding via `Url.queryAs` — the same wiring
  * (`decoder.decode(name, queryAll(name))`) used by `MeltContext.queryAs`.
  */
class UrlQueryAsTest extends munit.FunSuite:

  // A domain type decoded from a raw query value, like the OIDC codecs.
  case class RedirectUri(value: String)
  object RedirectUri:
    def fromString(s: String): Either[String, RedirectUri] =
      if s.startsWith("https://") then Right(RedirectUri(s)) else Left(s"insecure redirect_uri: $s")
  given FieldDecoder[RedirectUri] = FieldDecoder[String].emap(RedirectUri.fromString)

  private def url(params: (String, List[String])*): Url =
    Url("/authorize", params.toMap, "https://op.example")

  test("required scalar: present decodes, absent is a Left"):
    assertEquals(url("max_age" -> List("300")).queryAs[Long]("max_age"), Right(300L))
    assert(url().queryAs[Long]("max_age").isLeft)

  test("optional: absent decodes to None, present to Some"):
    assertEquals(url().queryAs[Option[Long]]("max_age"), Right(None))
    assertEquals(url("max_age" -> List("5")).queryAs[Option[Long]]("max_age"), Right(Some(5L)))

  test("domain type via emap: valid decodes, invalid is a Left"):
    assertEquals(
      url("redirect_uri" -> List("https://app/cb")).queryAs[RedirectUri]("redirect_uri"),
      Right(RedirectUri("https://app/cb"))
    )
    assert(url("redirect_uri" -> List("http://app/cb")).queryAs[RedirectUri]("redirect_uri").isLeft)

  test("Set collects repeated parameters"):
    assertEquals(url("id" -> List("1", "2", "2")).queryAs[Set[Int]]("id"), Right(Set(1, 2)))

  test("space-delimited single value via an explicit decoder"):
    given FieldDecoder[Set[String]] = FieldDecoder.spaceDelimited[String]
    assertEquals(url("scope" -> List("openid profile")).queryAs[Set[String]]("scope"), Right(Set("openid", "profile")))
