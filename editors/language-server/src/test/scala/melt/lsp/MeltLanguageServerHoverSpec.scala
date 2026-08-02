/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import org.eclipse.lsp4j.*

/** Tests for [[MeltLanguageServer.hover]] — script hovers delegate to Metals and
  * fall back to a section label when Metals is unavailable.
  *
  * The server is instantiated directly (no LSP4J launcher) so these tests avoid the
  * launcher-reflection path exercised by [[MeltLanguageServerIntegrationSpec]].
  */
class MeltLanguageServerHoverSpec extends munit.FunSuite:

  private val source =
    """|<script lang="scala">
       |  val count = 0
       |</script>
       |<div>{count}</div>""".stripMargin

  private def openedServer(uri: String): MeltLanguageServer =
    val server = new MeltLanguageServer
    server.didOpen(DidOpenTextDocumentParams(TextDocumentItem(uri, "melt", 1, source)))
    server

  private def hoverText(server: MeltLanguageServer, uri: String, line: Int, char: Int): String =
    val hover = server.hover(HoverParams(TextDocumentIdentifier(uri), Position(line, char))).get()
    hover.getContents.getRight.getValue

  test("hover on a script line falls back to the Scala section label when Metals is unavailable") {
    val uri = "file:///tmp/Counter.melt"
    // No initialize()/Metals process started, so hoverForScript yields None → fallback.
    val text = hoverText(openedServer(uri), uri, line = 1, char = 6)
    assert(text.contains("Scala script"), text)
  }

  test("hover on a template line returns the template section label") {
    val uri  = "file:///tmp/Counter.melt"
    val text = hoverText(openedServer(uri), uri, line = 3, char = 1)
    assert(text.contains("HTML template"), text)
  }
