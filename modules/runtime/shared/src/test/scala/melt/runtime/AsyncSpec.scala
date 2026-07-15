/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class AsyncSpec extends munit.FunSuite:

  test("map transforms only the Done case"):
    assertEquals(Async.Done(2).map(_ * 10), Async.Done(20))
    assertEquals((Async.Loading: Async[Int]).map(_ * 10), Async.Loading)
    val boom = new RuntimeException("x")
    assertEquals((Async.Failed(boom): Async[Int]).map(_ * 10), Async.Failed(boom))

  test("toOption / toError expose the settled value or error"):
    assertEquals(Async.Done(7).toOption, Some(7))
    assertEquals((Async.Loading: Async[Int]).toOption, None)
    val boom = new RuntimeException("x")
    assertEquals((Async.Failed(boom): Async[Int]).toError, Some(boom))
    assertEquals(Async.Done(7).toError, None)

  test("predicates classify each state"):
    assert((Async.Loading: Async[Int]).isLoading)
    assert(Async.Done(1).isDone)
    assert(Async.Failed(new RuntimeException).isFailed)

  test("fold collapses all three cases"):
    def label(a: Async[Int]): String = a.fold("loading")(e => s"err:${ e.getMessage }")(v => s"done:$v")
    assertEquals(label(Async.Loading), "loading")
    assertEquals(label(Async.Done(3)), "done:3")
    assertEquals(label(Async.Failed(new RuntimeException("bad"))), "err:bad")
