/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import scala.concurrent.Future

class AsyncStateSpec extends munit.FunSuite:

  import MeltEffect.given

  test("AsyncState.create starts in loading state") {
    Cleanup.pushScope()
    val state = AsyncState.create[Future, List[String]](Future.successful(List("Alice", "Bob")))
    val isLoading: Boolean = state.loading.value
    assert(isLoading, "should be loading initially")
    val v: Option[List[String]] = state.value.value
    assertEquals(v, None)
    Cleanup.popScope()
  }

  test("AsyncState.create on failure starts loading") {
    Cleanup.pushScope()
    val state = AsyncState.create[Future, String](Future.failed[String](new RuntimeException("fail")))
    val isLoading: Boolean = state.loading.value
    assert(isLoading, "should be loading initially")
    Cleanup.popScope()
  }

  test("AsyncState setSuccess updates value and clears loading") {
    val state = new AsyncState[String]
    state.setLoading()
    assert(state.loading.value, "should be loading")
    state.setSuccess("done")
    assert(!state.loading.value, "should not be loading after success")
    assertEquals(state.value.value, Some("done"))
    assertEquals(state.error.value, None)
  }

  test("AsyncState setFailure updates error and clears loading") {
    val state = new AsyncState[Int]
    state.setLoading()
    val err = new RuntimeException("boom")
    state.setFailure(err)
    assert(!state.loading.value, "should not be loading after failure")
    assertEquals(state.value.value, None)
    assertEquals(state.error.value, Some(err))
  }

  test("AsyncState.derived creates state from Var dependency") {
    Cleanup.pushScope()
    val userId = Var(1)
    val user   = AsyncState.derived[Future, Int, String](userId)(id => Future.successful(s"User-$id"))
    val isLoading: Boolean = user.loading.value
    assert(isLoading, "should be loading initially")
    Cleanup.popScope()
  }
