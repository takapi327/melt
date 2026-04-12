/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.json

import munit.FunSuite

import melt.runtime.json.SimpleJson.JsonValue

/** Unit tests for the hand-rolled JSON parser / encoder used by
  * [[PropsCodec]]. Focuses on the corner cases relevant to Props
  * serialisation: round-tripping primitive types, escape handling,
  * and the `</` break that protects against early `<script>` tag
  * termination.
  */
class SimpleJsonSpec extends FunSuite:

  // ── parse ─────────────────────────────────────────────────────────────

  test("parse: primitive object"):
    val v = SimpleJson.parse("""{"name":"Melt","count":42,"on":true,"nothing":null}""")
    v match
      case obj: JsonValue.Obj =>
        assertEquals(obj.getString("name"), Some("Melt"))
        assertEquals(obj.getInt("count"), Some(42))
        assertEquals(obj.getBool("on"), Some(true))
        assertEquals(obj.fields.get("nothing"), Some(JsonValue.Null))
      case other => fail(s"expected Obj, got $other")

  test("parse: nested object and array"):
    val v = SimpleJson.parse("""{"items":[{"id":1},{"id":2}]}""")
    v match
      case obj: JsonValue.Obj =>
        obj.getArr("items") match
          case Some(JsonValue.Obj(f1) :: JsonValue.Obj(f2) :: Nil) =>
            assertEquals(f1.get("id"), Some(JsonValue.Num(1.0)))
            assertEquals(f2.get("id"), Some(JsonValue.Num(2.0)))
          case other => fail(s"unexpected array shape: $other")
      case other => fail(s"expected Obj, got $other")

  test("parse: string escapes"):
    val v = SimpleJson.parse("""{"s":"a\"b\\c\n\u0041"}""")
    v match
      case obj: JsonValue.Obj => assertEquals(obj.getString("s"), Some("a\"b\\c\nA"))
      case other              => fail(s"expected Obj, got $other")

  test("parse: negative and decimal numbers"):
    val v = SimpleJson.parse("""{"n":-1.5e2}""")
    v match
      case obj: JsonValue.Obj => assertEquals(obj.getDouble("n"), Some(-150.0))
      case other              => fail(s"expected Obj, got $other")

  test("parse: rejects trailing garbage"):
    interceptMessage[IllegalArgumentException]("unexpected trailing input at offset 2"):
      SimpleJson.parse("{}x")

  // ── encString ─────────────────────────────────────────────────────────

  test("encString: wraps in quotes and escapes standard characters"):
    assertEquals(SimpleJson.encString("hello"), "\"hello\"")
    assertEquals(SimpleJson.encString("a\"b"), "\"a\\\"b\"")
    assertEquals(SimpleJson.encString("line1\nline2"), "\"line1\\nline2\"")

  test("encString: escapes </ so the JSON can live inside <script>"):
    // A literal `</script>` in the JSON payload must not be able to
    // terminate the enclosing `<script type="application/json">` tag.
    // We break up the `</` sequence by escaping the slash.
    val encoded = SimpleJson.encString("</script>")
    assert(!encoded.contains("</"), s"expected </ to be escaped, got $encoded")
    assertEquals(encoded, "\"<\\/script>\"")

  test("encString: escapes ASCII control characters as \\uXXXX"):
    assertEquals(SimpleJson.encString("\u0001"), "\"\\u0001\"")

  // ── encNumber ─────────────────────────────────────────────────────────

  test("encNumber: emits integral form when value is whole"):
    assertEquals(SimpleJson.encNumber(42.0), "42")
    assertEquals(SimpleJson.encNumber(-7.0), "-7")
    assertEquals(SimpleJson.encNumber(3.5), "3.5")

  test("encNumber: rejects NaN and infinity"):
    intercept[IllegalArgumentException](SimpleJson.encNumber(Double.NaN))
    intercept[IllegalArgumentException](SimpleJson.encNumber(Double.PositiveInfinity))

  // ── Obj helpers ───────────────────────────────────────────────────────

  test("Obj.getOpt returns None for missing and null fields"):
    val obj = SimpleJson.parse("""{"present":"x","absent":null}""").asInstanceOf[JsonValue.Obj]
    assertEquals(obj.getOpt("present")(_ => "ok"), Some("ok"))
    assertEquals(obj.getOpt("absent")(_ => "ok"), None)
    assertEquals(obj.getOpt("missing")(_ => "ok"), None)
