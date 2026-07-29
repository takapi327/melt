/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.{ ExecutionContext, Future, Promise }

import meltkit.FutureStreamBody

/** Unit test for the shared streaming driver used by the Node.js and Undertow
  * chunked-SSR transports: it must flush the head, then each boundary chunk as it
  * settles (out-of-order), then one merged `data-melt-queries` seed script + tail,
  * and finally close. A parasitic executor runs each promise's completion inline, so
  * the sink reflects the writes deterministically as the test settles the promises.
  */
class FutureStreamBodyTest extends munit.FunSuite:

  private given ExecutionContext = ExecutionContext.parasitic

  test("drive flushes the head, then chunks as they settle (out-of-order), merged seed, tail, close"):
    val sink   = new StringBuilder
    var closed = false
    val p1     = Promise[(String, List[(String, String)])]()
    val p2     = Promise[(String, List[(String, String)])]()
    val body   = FutureStreamBody("HEAD", List(p1.future, p2.future), "TAIL")

    FutureStreamBody.drive(
      body,
      s =>
        sink ++= s; Future.unit
      ,
      () => closed = true
    )

    // The head flushes immediately; nothing else until a boundary settles.
    assertEquals(sink.toString, "HEAD")

    // Settle the second-registered boundary first — it must flush first.
    p2.success(("<c2>", List("posts.get\n1" -> "[2]")))
    p1.success(("<c1>", List("nums.list\nnull" -> "[1]")))

    val out = sink.toString
    assert(out.indexOf("<c2>") < out.indexOf("<c1>"), out) // completion order, not registration
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
