/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CompletableFuture

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient

/** Tests that `didChange` debounces validation so a burst of keystrokes triggers a
  * single compile rather than one per keystroke.
  */
class DidChangeDebounceSpec extends munit.FunSuite:

  /** Minimal client that just counts publishDiagnostics calls (each fastValidate run
    * publishes once). Avoids the LSP4J launcher entirely. */
  private class CountingClient extends LanguageClient:
    val count = new AtomicInteger(0)
    override def telemetryEvent(o: Any):                          Unit = ()
    override def publishDiagnostics(p: PublishDiagnosticsParams): Unit =
      count.incrementAndGet(); ()
    override def showMessage(p: MessageParams):                   Unit                                 = ()
    override def showMessageRequest(p: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
      CompletableFuture.completedFuture(null)
    override def logMessage(p: MessageParams): Unit = ()

  private def change(uri: String, text: String): DidChangeTextDocumentParams =
    val p = DidChangeTextDocumentParams()
    p.setTextDocument(VersionedTextDocumentIdentifier(uri, 1))
    p.setContentChanges(java.util.List.of(TextDocumentContentChangeEvent(text)))
    p

  test("a burst of didChange events triggers a single debounced validation") {
    val client = new CountingClient
    val server = new MeltLanguageServer(debounceMs = 120)
    server.connect(client)
    val uri = "file:///tmp/A.melt"
    try
      (1 to 6).foreach(i => server.didChange(change(uri, s"<div>{x$i}</div>")))

      // Well within the debounce window: nothing has validated yet.
      Thread.sleep(20)
      assertEquals(client.count.get(), 0, "validation should be deferred, not per-keystroke")

      // Eventually exactly one validation fires for the whole burst.
      var waited = 0
      while client.count.get() < 1 && waited < 3000 do
        Thread.sleep(20); waited += 20
      assertEquals(client.count.get(), 1, "the burst should collapse to a single validation")
    finally server.shutdown()
  }
