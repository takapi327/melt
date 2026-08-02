/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind,
} from "vscode-languageclient/node";

let client: LanguageClient | undefined;
let outputChannel: vscode.LogOutputChannel | undefined;

export function activate(context: vscode.ExtensionContext): void {
  outputChannel = vscode.window.createOutputChannel("Melt Language Server", { log: true });
  context.subscriptions.push(outputChannel);

  context.subscriptions.push(
    vscode.commands.registerCommand("melt.restartServer", async () => {
      await stopClient();
      await startServer(context);
    }),
    vscode.commands.registerCommand("melt.showOutput", () => outputChannel?.show())
  );

  void startServer(context);
}

/** Resolves the server JAR + Java executable, then starts the language client.
 *
 * Every failure mode (missing JAR, missing/misconfigured Java, a server process
 * that fails to spawn) is surfaced via `showErrorMessage` with actionable text
 * instead of failing silently.
 */
async function startServer(context: vscode.ExtensionContext): Promise<void> {
  const config = vscode.workspace.getConfiguration("melt");

  // Resolve the server JAR: prefer the user-configured path, fall back to bundled.
  const serverJar: string =
    config.get<string>("server.path")?.trim() ||
    context.asAbsolutePath(path.join("server", "melt-language-server.jar"));

  if (!fs.existsSync(serverJar)) {
    fail(
      `Melt: language server JAR not found at "${serverJar}". ` +
        `Set "melt.server.path" to a melt-language-server JAR, or reinstall the extension with its bundled server.`
    );
    return;
  }

  const java = resolveJava(config);
  if (java.error) {
    fail(java.error);
    return;
  }

  const extraJavaArgs: string[] = config.get<string[]>("server.javaArgs") ?? [];

  const serverOptions: ServerOptions = {
    command: java.command,
    args: [...extraJavaArgs, "-jar", serverJar],
    transport: TransportKind.stdio,
  };

  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ scheme: "file", language: "melt" }],
    synchronize: {
      fileEvents: vscode.workspace.createFileSystemWatcher("**/*.melt"),
    },
    outputChannel,
  };

  client = new LanguageClient(
    "melt",
    "Melt Language Server",
    serverOptions,
    clientOptions
  );

  try {
    await client.start();
    context.subscriptions.push(client);
    outputChannel?.appendLine(`[melt] language server started (${java.command} -jar ${serverJar})`);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    fail(
      `Melt: failed to start the language server (${message}). ` +
        `Ensure Java is installed and on PATH, or set "melt.server.java". ` +
        `Run "Melt: Show Server Output" for details.`
    );
    client = undefined;
  }
}

/** Resolves the Java executable: `melt.server.java` → `$JAVA_HOME/bin/java` → PATH `java`.
 *
 * An explicitly-configured or `JAVA_HOME`-derived path is checked for existence so
 * the error is clear; the bare PATH fallback is left to the spawn (its `ENOENT` is
 * caught and surfaced by the `client.start()` handler).
 */
function resolveJava(config: vscode.WorkspaceConfiguration): { command: string; error?: string } {
  const exe = process.platform === "win32" ? "java.exe" : "java";

  const configured = config.get<string>("server.java")?.trim();
  if (configured) {
    if (!fs.existsSync(configured)) {
      return {
        command: configured,
        error: `Melt: the Java executable set in "melt.server.java" was not found: "${configured}".`,
      };
    }
    return { command: configured };
  }

  const javaHome = process.env.JAVA_HOME;
  if (javaHome) {
    const candidate = path.join(javaHome, "bin", exe);
    if (fs.existsSync(candidate)) {
      return { command: candidate };
    }
    outputChannel?.appendLine(
      `[melt] JAVA_HOME is set but no Java at ${candidate}; falling back to "java" on PATH`
    );
  }

  return { command: "java" };
}

function fail(message: string): void {
  void vscode.window.showErrorMessage(message);
  outputChannel?.appendLine(`[melt] ${message}`);
}

async function stopClient(): Promise<void> {
  if (client) {
    await client.stop().catch(() => undefined);
    client = undefined;
  }
}

export function deactivate(): Thenable<void> | undefined {
  return client?.stop();
}
