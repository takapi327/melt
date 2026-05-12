/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.util.concurrent.CompletableFuture
import java.util.Collections

import scala.concurrent.{ blocking, ExecutionContext, Future }
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

  private given ExecutionContext = ExecutionContext.global

  @volatile private var client:        Option[LanguageClient]     = None
  @volatile private var workspaceRoot: Option[java.nio.file.Path] = None
  private val metals:                  MetalsBridge               = MetalsBridge()

  /** In-memory index of importable files (path → CompletionItem).
    * Built once in [[initialized]] and kept up-to-date via [[didChangeWatchedFiles]].
    */
  private val fileIndex = scala.collection.concurrent.TrieMap.empty[String, CompletionItem]

  /** Open documents: URI → current .melt source text. */
  private val documents = scala.collection.concurrent.TrieMap.empty[String, String]

  // ── LanguageClientAware ───────────────────────────────────────────────────

  override def connect(languageClient: LanguageClient): Unit =
    this.client = Some(languageClient)

  // ── LanguageServer lifecycle ──────────────────────────────────────────────

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] =
    // Capture the workspace root so ImportPathCompletionProvider can scan for files.
    // Safe URI → Path conversion: invalid URIs must not crash initialize().
    def uriToPath(uri: String): Option[java.nio.file.Path] =
      Option(uri).filter(_.nonEmpty).flatMap { u =>
        try Some(java.nio.file.Paths.get(java.net.URI.create(u)))
        catch case _: Exception => None
      }
    workspaceRoot = uriToPath(params.getRootUri)
      .orElse(
        Option(params.getWorkspaceFolders)
          .flatMap(_.asScala.headOption)
          .flatMap(f => uriToPath(f.getUri))
      )

    // Start Metals in the background so initialize() returns immediately.
    Future(metals.startIfAvailable()).failed
      .foreach(e => System.err.println(s"[melt-lsp] Metals startup error: $e"))

    // Use TextDocumentSyncOptions (not the bare enum) so that the `save` capability
    // is registered. Without this, LSP clients do not send textDocument/didSave.
    val syncOpts = TextDocumentSyncOptions()
    syncOpts.setChange(TextDocumentSyncKind.Full)
    syncOpts.setOpenClose(true)
    syncOpts.setSave(SaveOptions(true)) // includeText=true

    val caps = ServerCapabilities()
    caps.setTextDocumentSync(JEither.forRight(syncOpts))
    caps.setHoverProvider(true)
    caps.setDefinitionProvider(true)
    // `"` and `/` are added as trigger characters so that completions activate
    // when the user opens or types a path inside `import "..."`.
    caps.setCompletionProvider(CompletionOptions(false, List(".", " ", "<", "{", "/", "\"").asJava))
    CompletableFuture.completedFuture(InitializeResult(caps))

  override def initialized(params: InitializedParams): Unit =
    // Build the file index in the background so initialized() returns immediately.
    workspaceRoot.foreach { root =>
      Future(blocking(ImportPathCompletionProvider.buildIndex(root)))
        .foreach(index => fileIndex ++= index)
    }
    // Register file watchers for supported extensions.
    // The client will send workspace/didChangeWatchedFiles when files are
    // created, changed, or deleted, allowing incremental index updates.
    client.foreach { c =>
      val globs = List(
        "**/*.css",
        "**/*.scss",
        "**/*.less",
        "**/*.sass",
        "**/*.js",
        "**/*.mjs",
        "**/*.ts",
        "**/*.jsx",
        "**/*.tsx"
      )
      val watchers = globs.map { g =>
        val w = new FileSystemWatcher()
        w.setGlobPattern(JEither.forLeft(g))
        w
      }
      val regOpts = new DidChangeWatchedFilesRegistrationOptions()
      regOpts.setWatchers(watchers.asJava)
      val reg = new Registration()
      reg.setId("melt-import-file-watcher")
      reg.setMethod("workspace/didChangeWatchedFiles")
      reg.setRegisterOptions(regOpts)
      c.registerCapability(new RegistrationParams(List(reg).asJava))
    }

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
    fastValidate(doc.getUri, doc.getText)
    Future(blocking(fullValidate(doc.getUri, doc.getText))).failed
      .foreach(e => System.err.println(s"[melt-lsp] fullValidate error for ${ doc.getUri }: $e"))

  override def didChange(params: DidChangeTextDocumentParams): Unit =
    val changes = params.getContentChanges
    if !changes.isEmpty then
      val text = changes.get(0).getText
      documents(params.getTextDocument.getUri) = text
      fastValidate(params.getTextDocument.getUri, text)

  override def didClose(params: DidCloseTextDocumentParams): Unit =
    val uri = params.getTextDocument.getUri
    documents.remove(uri)
    metals.closeDoc(uri)
    client.foreach(_.publishDiagnostics(PublishDiagnosticsParams(uri, Collections.emptyList())))

  override def didSave(params: DidSaveTextDocumentParams): Unit =
    val uri = params.getTextDocument.getUri
    // includeText=true guarantees getText() is non-null, but fall back to the last known text from didChange just in case.
    val text = Option(params.getText).getOrElse(documents.getOrElse(uri, ""))
    if documents.contains(uri) then documents(uri) = text
    Future(blocking(fullValidate(uri, text))).failed
      .foreach(e => System.err.println(s"[melt-lsp] fullValidate error for $uri: $e"))

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

    // ── Import path completions (highest priority when inside `import "..."`) ──
    // When the cursor is inside the string of an `import "..."` statement in the
    // script section, return only file-path completions and skip Melt/Metals items.
    val lineText = content.split("\n", -1).lift(line).getOrElse("")
    val importPathItems: List[CompletionItem] =
      if section == MeltSection.Script then
        ImportPathCompletionProvider
          .detectImportPrefix(lineText, char)
          .map(prefix => ImportPathCompletionProvider.completionsFor(prefix, fileIndex))
          .getOrElse(Nil)
      else Nil

    if importPathItems.nonEmpty then CompletableFuture.completedFuture(JEither.forLeft(importPathItems.asJava))
    else
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

  override def didChangeWatchedFiles(params: DidChangeWatchedFilesParams): Unit =
    workspaceRoot.foreach { root =>
      params.getChanges.asScala.foreach { event =>
        ImportPathCompletionProvider.handleFileEvent(root, event, fileIndex)
      }
    }

  // ── Diagnostics ───────────────────────────────────────────────────────────

  /** Runs meltc syntax and semantic checks only (fast path).
    * Used for real-time feedback while the user is typing.
    */
  private def fastValidate(uri: String, content: String): Unit =
    client.foreach { c =>
      val filename = uriToFilename(uri)
      val result   = meltc.MeltCompiler.compile(content, filename)
      val diags    =
        result.errors.map(e => makeDiagnostic(e.message, e.line, e.column, DiagnosticSeverity.Error)) ++
          result.warnings.map(w => makeDiagnostic(w.message, w.line, w.column, DiagnosticSeverity.Warning))
      c.publishDiagnostics(PublishDiagnosticsParams(uri, diags.asJava))
    }

  /** Runs meltc checks followed by Metals type-checking (slow path).
    * Executed asynchronously on didOpen / didSave.
    *
    * Metals type-checking is skipped when meltc reports errors, because the
    * generated virtual file may also be broken, producing a flood of spurious
    * type errors.
    *
    * The document is checked for presence at both the start and just before
    * publishing to guard against a concurrent didClose racing with this
    * long-running async operation.
    */
  private def fullValidate(uri: String, content: String): Unit =
    // Guard against races with didClose: skip if the client is not yet connected or the document is already closed.
    client.filter(_ => documents.contains(uri)).foreach { c =>
      val filename = uriToFilename(uri)
      val result   = meltc.MeltCompiler.compile(content, filename)

      val meltcDiags =
        result.errors.map(e => makeDiagnostic(e.message, e.line, e.column, DiagnosticSeverity.Error)) ++
          result.warnings.map(w => makeDiagnostic(w.message, w.line, w.column, DiagnosticSeverity.Warning))

      val metalsDiags: List[Diagnostic] =
        if result.errors.nonEmpty then Nil
        else
          val vf = VirtualFileGenerator.generate(content)
          metals.diagnosticsForScript(uri, vf)

      // Re-check before publishing: diagnosticsForScript may block for up to 30 s,
      // during which the editor may have closed the document or reopened it with
      // different content. Only publish if the document still contains the same text
      // that was validated to avoid showing stale diagnostics.
      if documents.get(uri).contains(content) then
        c.publishDiagnostics(PublishDiagnosticsParams(uri, (meltcDiags ++ metalsDiags).asJava))
    }

  private def makeDiagnostic(message: String, line: Int, column: Int, severity: DiagnosticSeverity): Diagnostic =
    val zeroLine = math.max(0, line - 1)
    val zeroCol  = math.max(0, column - 1)
    val range    = Range(Position(zeroLine, zeroCol), Position(zeroLine, Int.MaxValue))
    val d        = Diagnostic(range, message)
    d.setSeverity(severity)
    d.setSource("meltc")
    d

  private def uriToFilename(uri: String): String =
    val path = uri.stripPrefix("file:///").stripPrefix("file://").stripPrefix("file:")
    java.nio.file.Paths.get(path).getFileName.toString
