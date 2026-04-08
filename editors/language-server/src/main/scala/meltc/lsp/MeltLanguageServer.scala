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
  *   - `<script lang="scala">` body — meltc diagnostics + Metals completions/definition
  *   - HTML template `{expr}` blocks — Melt template completions + script-definition jump
  *   - `<style>` section           — CSS property completions
  *
  * The server communicates over stdio using the Language Server Protocol (JSON-RPC).
  * Start it with [[MeltLanguageServerLauncher]] and connect an editor LSP client.
  *
  * ==Completion strategy==
  *   1. [[MeltCompletionProvider]] — Melt-specific completions (always available)
  *   2. [[MetalsBridge]] — Scala completions for the script section (requires `metals` on PATH)
  *
  * ==Definition strategy==
  *   - Script section: delegated to Metals via [[MetalsBridge.definitionForScript]]
  *   - Template section: identifier under cursor is looked up in the script section
  *     using [[ScriptDefinitionFinder]]
  *   - Style section: no-op (returns empty)
  */
class MeltLanguageServer extends LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService:

  private var client: LanguageClient = scala.compiletime.uninitialized
  private val metals: MetalsBridge   = MetalsBridge()

  /** Open documents: URI → current .melt source text. */
  private val documents = scala.collection.concurrent.TrieMap.empty[String, String]

  // ── LanguageClientAware ───────────────────────────────────────────────────

  override def connect(languageClient: LanguageClient): Unit =
    this.client = languageClient

  // ── LanguageServer lifecycle ──────────────────────────────────────────────

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] =
    val _    = scala.concurrent.Future(metals.startIfAvailable())(scala.concurrent.ExecutionContext.global)
    val caps = ServerCapabilities()
    caps.setTextDocumentSync(TextDocumentSyncKind.Full)
    caps.setHoverProvider(true)
    caps.setDefinitionProvider(true)
    caps.setCompletionProvider(CompletionOptions(false, List(".", " ", "<", "{").asJava))
    CompletableFuture.completedFuture(InitializeResult(caps))

  override def initialized(params: InitializedParams): Unit = ()

  override def shutdown(): CompletableFuture[Object] =
    metals.shutdown()
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

  /** Returns a hover tooltip describing the section the cursor is in. */
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
    * The result set is the union of:
    *   - [[MeltCompletionProvider]] items for the current section (always present)
    *   - [[MetalsBridge]] items for the script section (present when `metals` is on PATH)
    *
    * Metals items are prepended so they appear first in the editor list.
    */
  override def completion(
    params: CompletionParams
  ): CompletableFuture[JEither[java.util.List[CompletionItem], CompletionList]] =
    val uri     = params.getTextDocument.getUri
    val content = documents.getOrElse(uri, "")
    val vf      = VirtualFileGenerator.generate(content)
    val line    = params.getPosition.getLine
    val char    = params.getPosition.getCharacter
    val section = vf.mapper.sectionAt(line)

    val meltItems   = MeltCompletionProvider.completionsFor(section)
    val metalsItems =
      if section == MeltSection.Script then metals.completionsForScript(uri, vf, line, char)
      else Nil

    CompletableFuture.completedFuture(JEither.forLeft((metalsItems ++ meltItems).asJava))

  /** Returns the definition location for the symbol under the cursor.
    *
    * Routing:
    *   - Script section  → [[MetalsBridge.definitionForScript]] (Metals, identity position mapping)
    *   - Template section → [[ScriptDefinitionFinder.find]] (identifier lookup in script body)
    *   - Style / Unknown → empty
    */
  override def definition(
    params: DefinitionParams
  ): CompletableFuture[JEither[java.util.List[? <: Location], java.util.List[? <: LocationLink]]] =
    val uri     = params.getTextDocument.getUri
    val content = documents.getOrElse(uri, "")
    val vf      = VirtualFileGenerator.generate(content)
    val line    = params.getPosition.getLine
    val char    = params.getPosition.getCharacter
    val section = vf.mapper.sectionAt(line)

    val locations: List[Location] = section match
      case MeltSection.Script =>
        metals.definitionForScript(uri, vf, line, char)
      case MeltSection.Template =>
        ScriptDefinitionFinder.find(content, uri, line, char, vf)
      case _ =>
        Nil

    CompletableFuture.completedFuture(
      JEither.forLeft(locations.asJava)
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
    val zeroLine = math.max(0, line - 1)
    val range    = Range(Position(zeroLine, 0), Position(zeroLine, Int.MaxValue))
    val d        = Diagnostic(range, message)
    d.setSeverity(severity)
    d.setSource("meltc")
    d

  private def uriToFilename(uri: String): String =
    val path = uri.stripPrefix("file:///").stripPrefix("file://").stripPrefix("file:")
    java.nio.file.Paths.get(path).getFileName.toString
