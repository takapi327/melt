/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.util.concurrent.CompletableFuture
import java.util.Collections

import scala.jdk.CollectionConverters.*

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either as JEither
import org.eclipse.lsp4j.services.*

/** Melt Language Server — routes each section of a .melt file to the
  * appropriate language tooling.
  *
  * Responsibilities per section:
  *   - `<script lang="scala">` body — meltc diagnostics; future: delegated to Metals
  *   - HTML template `{expr}` blocks — extracted for virtual file type checking
  *   - `<style>` section           — future: delegated to the CSS Language Server
  *
  * The server communicates over stdio using the Language Server Protocol (JSON-RPC).
  * Start it with [[MeltLanguageServerLauncher]] and connect an editor LSP client.
  *
  * ==Metals delegation (future)==
  * When Metals delegation is enabled the server will:
  *   1. Write the virtual .scala file (from [[VirtualFileGenerator]]) to a temp dir.
  *   2. Spawn a Metals process pointing at that directory.
  *   3. Forward `textDocument/completion`, `textDocument/hover`, and
  *      `textDocument/definition` requests with positions translated via
  *      [[PositionMapper.meltToVirtual]] and responses translated back via
  *      [[PositionMapper.virtualToMelt]].
  */
class MeltLanguageServer extends LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService:

  private var client: LanguageClient = scala.compiletime.uninitialized

  /** Open documents: URI → current .melt source text. */
  private val documents = scala.collection.concurrent.TrieMap.empty[String, String]

  // ── LanguageClientAware ───────────────────────────────────────────────────

  override def connect(languageClient: LanguageClient): Unit =
    this.client = languageClient

  // ── LanguageServer lifecycle ──────────────────────────────────────────────

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] =
    val caps = ServerCapabilities()
    caps.setTextDocumentSync(TextDocumentSyncKind.Full)
    caps.setHoverProvider(true)
    caps.setCompletionProvider(CompletionOptions(false, List(".").asJava))
    CompletableFuture.completedFuture(InitializeResult(caps))

  override def initialized(params: InitializedParams): Unit = ()

  override def shutdown(): CompletableFuture[Object] =
    CompletableFuture.completedFuture(null.asInstanceOf[Object])

  override def exit(): Unit = System.exit(0)

  override def getTextDocumentService(): TextDocumentService = this
  override def getWorkspaceService():    WorkspaceService    = this

  // ── TextDocumentService ───────────────────────────────────────────────────

  override def didOpen(params: DidOpenTextDocumentParams): Unit =
    val doc = params.getTextDocument
    documents(doc.getUri) = doc.getText
    validate(doc.getUri, doc.getText)

  override def didChange(params: DidChangeTextDocumentParams): Unit =
    val changes = params.getContentChanges
    if !changes.isEmpty then
      val text = changes.get(0).getText
      documents(params.getTextDocument.getUri) = text
      validate(params.getTextDocument.getUri, text)

  override def didClose(params: DidCloseTextDocumentParams): Unit =
    val uri = params.getTextDocument.getUri
    documents.remove(uri)
    if client != null then client.publishDiagnostics(PublishDiagnosticsParams(uri, Collections.emptyList()))

  override def didSave(params: DidSaveTextDocumentParams): Unit = ()

  /** Returns a hover tooltip showing which section the cursor is in.
    *
    * Future: delegate to Metals for script section hovers.
    */
  override def hover(params: HoverParams): CompletableFuture[Hover] =
    val uri     = params.getTextDocument.getUri
    val content = documents.getOrElse(uri, "")
    val vf      = VirtualFileGenerator.generate(content)
    val line    = params.getPosition.getLine
    val message = vf.mapper.sectionAt(line) match
      case MeltSection.Script   => "**Scala script** — type-checked by Metals"
      case MeltSection.Style    => "**CSS style** — scoped to this component"
      case MeltSection.Template => "**HTML template** — compiled to reactive DOM bindings"
      case MeltSection.Unknown  => "**Melt component**"
    val markup = MarkupContent(MarkupKind.MARKDOWN, message)
    CompletableFuture.completedFuture(Hover(markup))

  /** Returns completions for the cursor position.
    *
    * Future: delegate to Metals for script section completions.
    */
  override def completion(
    params: CompletionParams
  ): CompletableFuture[JEither[java.util.List[CompletionItem], CompletionList]] =
    CompletableFuture.completedFuture(
      JEither.forLeft(Collections.emptyList[CompletionItem]())
    )

  // ── WorkspaceService ──────────────────────────────────────────────────────

  override def didChangeConfiguration(params: DidChangeConfigurationParams): Unit = ()
  override def didChangeWatchedFiles(params:  DidChangeWatchedFilesParams):  Unit = ()

  // ── Diagnostics ───────────────────────────────────────────────────────────

  private def validate(uri: String, content: String): Unit =
    if client == null then return
    val filename = uriToFilename(uri)
    val result   = meltc.MeltCompiler.compile(content, filename)
    val diags    =
      result.errors.map(e => makeDiagnostic(e.message, e.line, DiagnosticSeverity.Error)) ++
        result.warnings.map(w => makeDiagnostic(w.message, w.line, DiagnosticSeverity.Warning))
    client.publishDiagnostics(PublishDiagnosticsParams(uri, diags.asJava))

  private def makeDiagnostic(message: String, line: Int, severity: DiagnosticSeverity): Diagnostic =
    // meltc uses 1-based line numbers; LSP uses 0-based
    val zeroLine = math.max(0, line - 1)
    val range    = Range(Position(zeroLine, 0), Position(zeroLine, Int.MaxValue))
    val d        = Diagnostic(range, message)
    d.setSeverity(severity)
    d.setSource("meltc")
    d

  private def uriToFilename(uri: String): String =
    val path = uri.stripPrefix("file:///").stripPrefix("file://").stripPrefix("file:")
    java.nio.file.Paths.get(path).getFileName.toString
