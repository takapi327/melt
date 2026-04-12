/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.json

import munit.FunSuite

/** Round-trip tests for [[PropsCodec]].
  *
  * These exercise the inline Mirror-based derivation across the
  * combinations that real component Props actually use:
  *
  *   - primitive fields
  *   - `Option[T]` and `List[T]`
  *   - a nested case class declared in the same file (emulates
  *     `case class Todos.Todo` inside a `Todos.melt` script block)
  *   - a case class imported from a sibling file (here we simply
  *     define it at the top of the spec — the derivation mechanism
  *     is agnostic to the declaration site, which is the main
  *     benefit over a structural-parser-based approach)
  */
class PropsCodecSpec extends FunSuite:

  // ── Fixtures ──────────────────────────────────────────────────────────

  final case class Primitive(
    s: String,
    i: Int,
    l: Long,
    d: Double,
    f: Float,
    b: Boolean
  ) derives PropsCodec

  final case class WithOption(
    name:     String,
    nickname: Option[String],
    count:    Option[Int]
  ) derives PropsCodec

  final case class Todo(id: String, text: String, done: Boolean) derives PropsCodec
  final case class TodoList(title: String, items: List[Todo]) derives PropsCodec

  // ── Primitive round-trip ─────────────────────────────────────────────

  test("primitives round-trip through encode/decode"):
    val p       = Primitive("Melt", 42, 1_000_000L, 3.14, 2.5f, true)
    val codec   = summon[PropsCodec[Primitive]]
    val encoded = codec.encodeToString(p)
    val decoded = codec.decode(SimpleJson.parse(encoded))
    assertEquals(decoded, p)

  test("primitives: numeric whole doubles serialise compactly"):
    val codec   = summon[PropsCodec[Primitive]]
    val encoded = codec.encodeToString(Primitive("a", 1, 2L, 3.0, 4.0f, false))
    // Integer-valued doubles should appear as "3", not "3.0", so the
    // payload embedded into HTML stays small.
    assert(encoded.contains("\"d\":3"), s"expected compact integer form, got $encoded")
    assert(encoded.contains("\"f\":4"), s"expected compact integer form, got $encoded")

  // ── Option ────────────────────────────────────────────────────────────

  test("Option fields round-trip (Some and None)"):
    val codec = summon[PropsCodec[WithOption]]

    val withSome    = WithOption("Alice", Some("Ali"), Some(3))
    val encodedSome = codec.encodeToString(withSome)
    assertEquals(codec.decode(SimpleJson.parse(encodedSome)), withSome)

    val withNone    = WithOption("Bob", None, None)
    val encodedNone = codec.encodeToString(withNone)
    // None is serialised as JSON null.
    assert(encodedNone.contains("\"nickname\":null"), encodedNone)
    assert(encodedNone.contains("\"count\":null"), encodedNone)
    assertEquals(codec.decode(SimpleJson.parse(encodedNone)), withNone)

  // ── List / nested case class ─────────────────────────────────────────

  test("List of nested case class round-trips"):
    val list = TodoList(
      title = "Today",
      items = List(
        Todo("1", "Write Props serialisation", done = true),
        Todo("2", "Ship it", done                   = false)
      )
    )
    val codec   = summon[PropsCodec[TodoList]]
    val encoded = codec.encodeToString(list)
    assertEquals(codec.decode(SimpleJson.parse(encoded)), list)

  test("empty List encodes to []"):
    val codec = summon[PropsCodec[TodoList]]
    val out   = codec.encodeToString(TodoList("none", Nil))
    assert(out.contains("\"items\":[]"), out)

  // ── Escaping / safety ────────────────────────────────────────────────

  test("String fields containing </script> are escaped"):
    val codec = summon[PropsCodec[Primitive]]
    val p     = Primitive("</script>", 0, 0L, 0.0, 0.0f, false)
    val out   = codec.encodeToString(p)
    assert(!out.contains("</"), s"</ should be broken up, got $out")

  test("decoding a mismatched shape raises a clear error"):
    val codec = summon[PropsCodec[Primitive]]
    val ex    = intercept[IllegalArgumentException] {
      codec.decode(SimpleJson.parse("""{"s":42,"i":1,"l":2,"d":3,"f":4,"b":true}"""))
    }
    assert(ex.getMessage.contains("expected String"), ex.getMessage)
