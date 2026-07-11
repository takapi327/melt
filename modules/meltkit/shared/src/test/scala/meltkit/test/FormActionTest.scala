/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.json.PropsCodec

import meltkit.*

class FormActionTest extends munit.FunSuite:

  case class TestForm(name: String = "", errors: List[String] = Nil) derives PropsCodec

  // ── fail ────────────────────────────────────────────────────────────────

  test("fail produces an ActionResult.Failure carrying the status and data"):
    meltkit.fail(422, TestForm("bob", List("bad"))) match
      case ActionResult.Failure(status, data) =>
        assertEquals(status, (422: StatusCode))
        assertEquals(data, TestForm("bob", List("bad")))
      case other => throw new AssertionError(s"expected Failure, got $other")

  // ── ActionResult.toJson (enhance envelope) ────────────────────────────────

  test("toJson serialises Success as a success envelope with data"):
    val json = ActionResult.toJson(ActionResult.Success(TestForm("bob")))
    assert(json.contains(""""type":"success""""), json)
    assert(json.contains(""""status":200"""), json)
    assert(json.contains(""""name":"bob""""), json)

  test("toJson serialises Failure with its status"):
    val json = ActionResult.toJson(meltkit.fail(422, TestForm("bob", List("required"))))
    assert(json.contains(""""type":"failure""""), json)
    assert(json.contains(""""status":422"""), json)
    assert(json.contains(""""required""""), json)

  test("toJson serialises Redirect as 303 with the location"):
    val json = ActionResult.toJson[TestForm](ActionResult.Redirect("/dashboard"))
    assert(json.contains(""""type":"redirect""""), json)
    assert(json.contains(""""status":303"""), json)
    assert(json.contains(""""location":"/dashboard""""), json)

  // ── Response.seeOther (303 PRG) ───────────────────────────────────────────

  test("Response.seeOther produces 303 with a Location header"):
    val r = Response.seeOther("/dashboard")
    assertEquals(r.status, (303: StatusCode))
    assertEquals(r.headers.get("Location"), Some("/dashboard"))

  test("Response.seeOther rejects open-redirect targets"):
    intercept[IllegalArgumentException](Response.seeOther("//evil.com"))
    intercept[IllegalArgumentException](Response.seeOther("https://evil.com"))
