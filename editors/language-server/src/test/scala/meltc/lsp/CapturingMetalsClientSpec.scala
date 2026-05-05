/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.util.concurrent.{ CompletableFuture, TimeUnit }

import scala.jdk.CollectionConverters.*

import org.eclipse.lsp4j.*

/** Unit tests for [[CapturingMetalsClient]] debounce and thread-safety logic.
  *
  * All tests use a short `debounceMs` (20 ms) so the debounce fires quickly.
  * A generous `get` timeout (5 s) avoids flaky failures on slow CI machines.
  */
class CapturingMetalsClientSpec extends munit.FunSuite:

  private val timeoutSec = 5L

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def makeClient(debounceMs: Long = 20L) = new CapturingMetalsClient(debounceMs)

  private def publish(cc: CapturingMetalsClient, uri: String, msgs: String*): Unit =
    val diags = msgs.map { msg =>
      val d = new Diagnostic()
      d.setMessage(msg)
      d
    }.toList
    cc.publishDiagnostics(PublishDiagnosticsParams(uri, diags.asJava))

  // ── Debounce delivery ─────────────────────────────────────────────────────

  test("debounce delivers settled diagnostics after silence") {
    val cc      = makeClient()
    val promise = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", promise)
    publish(cc, "file:///test.scala", "error 1")

    val result = promise.get(timeoutSec, TimeUnit.SECONDS)
    assertEquals(result.size, 1)
    assertEquals(result.head.getMessage.getLeft, "error 1")
    cc.shutdownScheduler()
  }

  test("debounce delivers last diagnostics when multiple notifications arrive") {
    val cc      = makeClient(debounceMs = 80L)
    val promise = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", promise)
    // Rapid-fire two notifications — only the second should be delivered
    publish(cc, "file:///test.scala", "first")
    publish(cc, "file:///test.scala", "second")

    val result = promise.get(timeoutSec, TimeUnit.SECONDS)
    assertEquals(result.size, 1)
    assertEquals(result.head.getMessage.getLeft, "second")
    cc.shutdownScheduler()
  }

  test("debounce fires independently per URI") {
    val cc = makeClient()
    val p1 = CompletableFuture[List[Diagnostic]]()
    val p2 = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///a.scala", p1)
    cc.expectDiagnostics("file:///b.scala", p2)
    publish(cc, "file:///a.scala", "a-error")
    publish(cc, "file:///b.scala", "b-error")

    val r1 = p1.get(timeoutSec, TimeUnit.SECONDS)
    val r2 = p2.get(timeoutSec, TimeUnit.SECONDS)
    assertEquals(r1.head.getMessage.getLeft, "a-error")
    assertEquals(r2.head.getMessage.getLeft, "b-error")
    cc.shutdownScheduler()
  }

  test("empty diagnostic list is delivered correctly") {
    val cc      = makeClient()
    val promise = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///clean.scala", promise)
    publish(cc, "file:///clean.scala") // no messages → empty list

    val result = promise.get(timeoutSec, TimeUnit.SECONDS)
    assert(result.isEmpty, s"expected empty diagnostics, got: $result")
    cc.shutdownScheduler()
  }

  // ── dropUri ───────────────────────────────────────────────────────────────

  test("dropUri cancels the pending promise without completing it") {
    val cc      = makeClient(debounceMs = 500L)
    val promise = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", promise)
    publish(cc, "file:///test.scala", "irrelevant")

    cc.dropUri("file:///test.scala")

    assert(promise.isCancelled, "promise should be cancelled after dropUri")
    cc.shutdownScheduler()
  }

  test("dropUri clears state so a subsequent expectDiagnostics starts fresh") {
    val cc = makeClient()

    val first = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", first)
    publish(cc, "file:///test.scala", "first")
    cc.dropUri("file:///test.scala")

    // Re-register after drop — should receive only the new notification
    val second = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", second)
    publish(cc, "file:///test.scala", "second")

    val result = second.get(timeoutSec, TimeUnit.SECONDS)
    assertEquals(result.size, 1)
    assertEquals(result.head.getMessage.getLeft, "second")
    cc.shutdownScheduler()
  }

  // ── expectDiagnostics — race prevention ───────────────────────────────────

  test("expectDiagnostics cancels old promise when called twice for the same URI") {
    val cc         = makeClient(debounceMs = 200L)
    val oldPromise = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", oldPromise)
    publish(cc, "file:///test.scala", "first compilation")

    // Register new promise before the first debounce fires
    val newPromise = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///test.scala", newPromise)
    publish(cc, "file:///test.scala", "second compilation")

    assert(oldPromise.isCancelled, "old promise should be cancelled when replaced")
    val result = newPromise.get(timeoutSec, TimeUnit.SECONDS)
    assertEquals(result.head.getMessage.getLeft, "second compilation")
    cc.shutdownScheduler()
  }

  // ── dropAll ───────────────────────────────────────────────────────────────

  test("dropAll cancels all pending promises") {
    val cc = makeClient(debounceMs = 500L)
    val p1 = CompletableFuture[List[Diagnostic]]()
    val p2 = CompletableFuture[List[Diagnostic]]()
    cc.expectDiagnostics("file:///a.scala", p1)
    cc.expectDiagnostics("file:///b.scala", p2)
    publish(cc, "file:///a.scala", "a")
    publish(cc, "file:///b.scala", "b")

    cc.dropAll()

    assert(p1.isCancelled, "p1 should be cancelled by dropAll")
    assert(p2.isCancelled, "p2 should be cancelled by dropAll")
    cc.shutdownScheduler()
  }

  // ── shutdownScheduler ─────────────────────────────────────────────────────

  test("publishDiagnostics after shutdownScheduler does not throw") {
    val cc = makeClient()
    cc.shutdownScheduler()
    // Should not throw RejectedExecutionException
    publish(cc, "file:///test.scala", "after shutdown")
  }
