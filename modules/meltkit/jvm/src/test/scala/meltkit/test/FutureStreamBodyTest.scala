/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ ExecutionContext, Future }

import meltkit.FutureStreamBody

/** Unit test for the shared streaming driver used by the Node.js and Undertow
  * chunked-SSR transports: it must flush the head, then each boundary chunk in
  * registration order, then one merged `data-melt-queries` seed script + tail, and
  * finally close. A parasitic executor makes the already-completed futures run
  * inline, so the sink is fully populated once `drive` returns.
  */
class FutureStreamBodyTest extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  test("drive writes head, chunks in order, a merged seed script, then tail, then closes"):
    val sink   = new StringBuilder
    var closed = false
    val body   = FutureStreamBody(
      head   = "HEAD",
      chunks = List(
        Future.successful(("<c1>", List("nums.list\nnull" -> "[1]"))),
        Future.successful(("<c2>", List("posts.get\n1" -> "[2]")))
      ),
      tail = "TAIL"
    )

    FutureStreamBody.drive(
      body,
      s =>
        sink ++= s; Future.unit
      ,
      () => closed = true
    )

    val out = sink.toString
    assert(out.startsWith("HEAD"), out)
    assert(out.indexOf("<c1>") < out.indexOf("<c2>"), out) // registration order preserved
    // Seeds from every chunk merge into a single tail data-melt-queries script.
    assertEquals(out.split("data-melt-queries").length - 1, 1, out)
    assert(out.contains("\"nums.list\\nnull\":[1]"), out)
    assert(out.contains("\"posts.get\\n1\":[2]"), out)
    assert(out.endsWith("TAIL"), out)
    assert(closed, "close callback must run")

  test("with no seeds, no data-melt-queries script is emitted"):
    val sink   = new StringBuilder
    var closed = false
    val body   = FutureStreamBody("HEAD", List(Future.successful(("<c1>", Nil))), "TAIL")

    FutureStreamBody.drive(
      body,
      s =>
        sink ++= s; Future.unit
      ,
      () => closed = true
    )

    val out = sink.toString
    assert(!out.contains("data-melt-queries"), out)
    assert(out == "HEAD<c1>TAIL", out)
    assert(closed)
