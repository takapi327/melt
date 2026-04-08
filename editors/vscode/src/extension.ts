/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import * as path from "path";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind,
} from "vscode-languageclient/node";

let client: LanguageClient | undefined;

export function activate(context: vscode.ExtensionContext): void {
  const config = vscode.workspace.getConfiguration("melt");

  // Resolve the server JAR path: prefer user-configured path, fall back to bundled.
  const serverJar: string =
    config.get<string>("server.path") ||
    context.asAbsolutePath(path.join("server", "melt-language-server.jar"));

  const extraJavaArgs: string[] = config.get<string[]>("server.javaArgs") ?? [];

  const serverOptions: ServerOptions = {
    command: "java",
    args: [...extraJavaArgs, "-jar", serverJar],
    transport: TransportKind.stdio,
  };

  const clientOptions: LanguageClientOptions = {
    // Activate for .melt files
    documentSelector: [{ scheme: "file", language: "melt" }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher("**/*.melt"),
    },
    traceOutputChannel: vscode.window.createOutputChannel("Melt Language Server Trace"),
  };

  client = new LanguageClient(
    "melt",
    "Melt Language Server",
    serverOptions,
    clientOptions
  );

  client.start();
  context.subscriptions.push(client);
}

export function deactivate(): Thenable<void> | undefined {
  return client?.stop();
}
