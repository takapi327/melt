/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import munit.FunSuite

/** Phase C §12.1.7 tests for [[HydrationMarkers]]. */
class HydrationMarkersSpec extends FunSuite:

  test("open produces <!--[melt:NAME--> for a plain moduleID") {
    assertEquals(HydrationMarkers.open("counter"), "<!--[melt:counter-->")
  }

  test("close produces <!--]melt:NAME-->") {
    assertEquals(HydrationMarkers.close("counter"), "<!--]melt:counter-->")
  }

  test("escapeForComment escapes < and >") {
    assertEquals(
      HydrationMarkers.escapeForComment("bad<name>bad"),
      "bad&#x3c;name&#x3e;bad"
    )
  }

  test("escapeForComment breaks a -- sequence so the comment stays valid") {
    val escaped = HydrationMarkers.escapeForComment("ev-il-->end")
    assert(!escaped.contains("-->"), escaped)
    assert(!escaped.contains("--"), escaped)
  }

  test("escapeForComment drops control characters") {
    assertEquals(
      HydrationMarkers.escapeForComment("a\u0000b\u0001c\u007Fd"),
      "abcd"
    )
  }

  test("escapeForComment tolerates null input") {
    assertEquals(HydrationMarkers.escapeForComment(null), "")
  }

  test("open + close round-trip for a hostile module ID") {
    // Attempt to break out of the surrounding comment.
    val evil = "x--><script>alert(1)</script>"
    val o    = HydrationMarkers.open(evil)
    val c    = HydrationMarkers.close(evil)

    // Extract the escaped payload (without the trailing "-->") and make
    // sure it cannot prematurely end the comment.
    val oPayload = o.substring(HydrationMarkers.OpenPrefix.length, o.length - HydrationMarkers.Suffix.length)
    val cPayload = c.substring(HydrationMarkers.ClosePrefix.length, c.length - HydrationMarkers.Suffix.length)
    assert(!oPayload.contains("-->"), oPayload)
    assert(!cPayload.contains("-->"), cPayload)
    assert(!oPayload.contains("--"), oPayload)
    assert(!cPayload.contains("--"), cPayload)
    assert(!o.contains("<script>"), o)
    assert(!c.contains("<script>"), c)
  }

  test("open / close for multi-word moduleIDs preserve the id verbatim") {
    assertEquals(HydrationMarkers.open("todo-list"), "<!--[melt:todo-list-->")
    assertEquals(HydrationMarkers.close("todo-list"), "<!--]melt:todo-list-->")
  }
