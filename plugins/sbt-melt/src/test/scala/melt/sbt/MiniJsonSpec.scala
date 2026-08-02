/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import melt.sbt.MiniJson.*

/** Tests for the minimal JSON reader/writer used to rewrite source-map files. */
class MiniJsonSpec extends munit.FunSuite:

  test("parses a source-map-shaped object and preserves key order on render") {
    val json =
      """{"version":3,"file":"main.js","sources":["a.scala","b.scala"],"names":[],"mappings":"AAAA;AACA"}"""
    val parsed = MiniJson.parse(json)
    assertEquals(MiniJson.render(parsed), json)
  }

  test("decodes string escapes and re-encodes them") {
    val parsed = MiniJson.parse(""""a\"b\\c\n"""")
    assertEquals(parsed.asString, "a\"b\\c\n")
    assertEquals(MiniJson.render(parsed), """"a\"b\\c\n"""")
  }

  test("withField replaces an existing key in place and appends a new one") {
    val obj = MiniJson.parse("""{"a":1,"b":2}""").asObject
    assertEquals(MiniJson.render(obj.withField("a", JStr("x"))), """{"a":"x","b":2}""")
    assertEquals(MiniJson.render(obj.withField("c", JStr("y"))), """{"a":1,"b":2,"c":"y"}""")
  }

  test("handles nested arrays and null in sourcesContent") {
    val json   = """{"sources":["a"],"sourcesContent":[null],"mappings":""}"""
    val parsed = MiniJson.parse(json)
    assertEquals(MiniJson.render(parsed), json)
  }
