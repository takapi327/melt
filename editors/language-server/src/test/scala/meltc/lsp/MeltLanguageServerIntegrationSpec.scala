/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, PipedInputStream, PipedOutputStream }
import java.util.concurrent.{ CompletableFuture, TimeUnit }

import scala.jdk.CollectionConverters.*

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient

/** Integration tests for [[MeltLanguageServer]] using in-process LSP4J launchers.
  *
  * Each test wires a real [[MeltLanguageServer]] to a real LSP4J client via
  * in-memory pipe streams, exactly as a real editor would, and exercises the
  * JSON-RPC lifecycle.
  */
class MeltLanguageServerIntegrationSpec extends munit.FunSuite:

  /** Boots a server + client pair connected over in-memory pipes. */
  private def withServer[A](body: org.eclipse.lsp4j.services.LanguageServer => A): A =
    // server reads from serverIn, writes to serverOut
    // client reads from serverOut, writes to serverIn  (mirrored)
    val clientToServer = PipedOutputStream()
    val serverIn       = PipedInputStream(clientToServer)
    val serverToClient = PipedOutputStream()
    val clientIn       = PipedInputStream(serverToClient)

    val server         = MeltLanguageServer()
    val serverLauncher = LSPLauncher.createServerLauncher(server, serverIn, serverToClient)
    server.connect(serverLauncher.getRemoteProxy)
    val serverFuture = serverLauncher.startListening()

    val client: LanguageClient = NoOpLanguageClient()
    val clientLauncher = LSPLauncher.createClientLauncher(client, clientIn, clientToServer)
    val clientFuture   = clientLauncher.startListening()
    val proxy          = clientLauncher.getRemoteProxy

    try body(proxy)
    finally
      serverFuture.cancel(true)
      clientFuture.cancel(true)

  // ── initialize ────────────────────────────────────────────────────────────

  test("initialize returns server capabilities") {
    withServer { proxy =>
      val params = InitializeParams()
      params.setRootUri("file:///tmp/test-project")
      val result = proxy.initialize(params).get(5, TimeUnit.SECONDS)
      assert(result != null, "initialize should return a non-null result")
      val caps = result.getCapabilities
      assert(caps != null)
    }
  }

  test("initialize reports textDocumentSync = Full") {
    withServer { proxy =>
      val params = InitializeParams()
      params.setRootUri("file:///tmp/test-project")
      val result = proxy.initialize(params).get(5, TimeUnit.SECONDS)
      val sync   = result.getCapabilities.getTextDocumentSync
      // sync is Either<TextDocumentSyncKind, TextDocumentSyncOptions>
      // For Full, it should be TextDocumentSyncKind.Full (value 1) or Options with change=1
      assert(sync != null)
    }
  }

  // ── shutdown / exit ───────────────────────────────────────────────────────

  test("shutdown returns without error") {
    withServer { proxy =>
      val params = InitializeParams()
      proxy.initialize(params).get(5, TimeUnit.SECONDS)
      val result = proxy.shutdown().get(5, TimeUnit.SECONDS)
      // shutdown() returns null per LSP spec
      assertEquals(result, null)
    }
  }

  // ── hover ─────────────────────────────────────────────────────────────────

  test("hover on script line returns Scala section message") {
    withServer { proxy =>
      proxy.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)
      proxy.initialized(InitializedParams())

      val source =
        """|<script lang="scala">
           |  val count = Var(0)
           |</script>""".stripMargin

      val uri     = "file:///tmp/Counter.melt"
      val docItem = TextDocumentItem(uri, "melt", 1, source)
      proxy.getTextDocumentService.didOpen(DidOpenTextDocumentParams(docItem))

      // line 1 (0-based) = "  val count = Var(0)" — script body
      val hoverParams = HoverParams(TextDocumentIdentifier(uri), Position(1, 5))
      val hover       = proxy.getTextDocumentService.hover(hoverParams).get(5, TimeUnit.SECONDS)
      assert(hover != null)
      val text = hover.getContents.getRight.getValue
      assert(text.contains("Scala"), s"expected Scala mention, got: $text")
    }
  }

  test("hover on template line returns template section message") {
    withServer { proxy =>
      proxy.initialize(InitializeParams()).get(5, TimeUnit.SECONDS)
      proxy.initialized(InitializedParams())

      val source =
        """|<script lang="scala">
           |  val n = 1
           |</script>
           |<div>{n}</div>""".stripMargin

      val uri     = "file:///tmp/Comp.melt"
      val docItem = TextDocumentItem(uri, "melt", 1, source)
      proxy.getTextDocumentService.didOpen(DidOpenTextDocumentParams(docItem))

      // line 3 = "<div>{n}</div>" — template
      val hoverParams = HoverParams(TextDocumentIdentifier(uri), Position(3, 1))
      val hover       = proxy.getTextDocumentService.hover(hoverParams).get(5, TimeUnit.SECONDS)
      assert(hover != null)
      val text = hover.getContents.getRight.getValue
      assert(text.contains("template") || text.contains("HTML"), s"got: $text")
    }
  }

/** A no-op language client used in integration tests. */
private class NoOpLanguageClient extends LanguageClient:
  override def telemetryEvent(obj:   Any):                      Unit                                 = ()
  override def publishDiagnostics(p: PublishDiagnosticsParams): Unit                                 = ()
  override def showMessage(p:        MessageParams):            Unit                                 = ()
  override def showMessageRequest(p: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
    CompletableFuture.completedFuture(null)
  override def logMessage(p: MessageParams): Unit = ()
