/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class KeyedListSpec extends munit.FunSuite:

  test("keyed extension creates KeyedList from Var[List]") {
    val items = Var(List(1, 2, 3))
    val kl    = items.keyed(identity)
    assertEquals(kl.source, items)
    assertEquals(kl.keyFn(42), 42)
  }

  test("keyed preserves key function") {
    case class Item(id: Int, name: String)
    val items = Var(List(Item(1, "a"), Item(2, "b")))
    val kl    = items.keyed(_.id)
    assertEquals(kl.keyFn(Item(3, "c")), 3)
  }
