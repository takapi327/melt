/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

class RefSpec extends munit.FunSuite:

  test("Ref.empty starts as None") {
    val ref = Ref.empty[dom.Element]
    assertEquals(ref.get, None)
  }

  test("foreach does nothing when empty") {
    val ref  = Ref.empty[dom.Element]
    var called = false
    ref.foreach(_ => called = true)
    assert(!called)
  }
