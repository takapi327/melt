/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

/** Unit tests for [[meltkit.Router]] on Node.js (AsyncLocalStorage-based).
  *
  * Verifies that [[meltkit.Router.withPath]] correctly scopes
  * [[meltkit.Router.currentPath]] for the duration of each call, and that
  * the default value is returned outside any `withPath` context.
  */
class RouterNodeTest extends munit.FunSuite:

  test("currentPath defaults to / outside withPath"):
    assertEquals(meltkit.Router.currentPath.value, "/")

  test("withPath scopes currentPath to the given path"):
    val result = meltkit.Router.withPath("/users/42") {
      meltkit.Router.currentPath.value
    }
    assertEquals(result, "/users/42")

  test("currentPath returns / after withPath exits"):
    meltkit.Router.withPath("/tmp") { () }
    assertEquals(meltkit.Router.currentPath.value, "/")

  test("nested withPath: inner path takes precedence"):
    val result = meltkit.Router.withPath("/outer") {
      meltkit.Router.withPath("/inner") {
        meltkit.Router.currentPath.value
      }
    }
    assertEquals(result, "/inner")

  test("nested withPath: outer path is restored after inner exits"):
    val outerDuring = meltkit.Router.withPath("/outer") {
      meltkit.Router.withPath("/inner") { () }
      meltkit.Router.currentPath.value
    }
    assertEquals(outerDuring, "/outer")

  test("withPath returns the value produced by the block"):
    val value = meltkit.Router.withPath("/path") { 42 }
    assertEquals(value, 42)

  test("navigate is a no-op (returns Unit)"):
    meltkit.Router.navigate("/anything") // must not throw

  test("replace is a no-op (returns Unit)"):
    meltkit.Router.replace("/anything") // must not throw
