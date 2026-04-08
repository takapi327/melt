/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import org.eclipse.lsp4j.launch.LSPLauncher

/** Launches the Melt Language Server over stdio.
  *
  * The server reads JSON-RPC messages from stdin and writes responses to stdout.
  * Editor LSP clients (VS Code, Neovim, IntelliJ) start this process and connect
  * to its stdio streams.
  *
  * {{{
  *   java -jar melt-language-server.jar
  * }}}
  */
object MeltLanguageServerLauncher:

  def main(args: Array[String]): Unit =
    val server   = MeltLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, System.in, System.out)
    server.connect(launcher.getRemoteProxy)
    launcher.startListening().get()
