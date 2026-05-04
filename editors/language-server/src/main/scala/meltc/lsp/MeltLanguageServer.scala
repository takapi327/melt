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

  private var client: Option[LanguageClient] = None
  private val metals: MetalsBridge           = MetalsBridge()

  /** Open documents: URI → current .melt source text. */
  private val documents = scala.collection.concurrent.TrieMap.empty[String, String]

  // ── LanguageClientAware ───────────────────────────────────────────────────

  override def connect(languageClient: LanguageClient): Unit =
    this.client = Some(languageClient)

  // ── LanguageServer lifecycle ──────────────────────────────────────────────

  override def initialize(params: InitializeParams): CompletableFuture[InitializeResult] =
    val _ = scala.concurrent.Future(metals.startIfAvailable())(scala.concurrent.ExecutionContext.global)

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
    fastValidate(doc.getUri, doc.getText)
    scala.concurrent
      .Future {
        scala.concurrent.blocking { fullValidate(doc.getUri, doc.getText) }
      }(scala.concurrent.ExecutionContext.global)
      .failed
      .foreach(e => System.err.println(s"[melt-lsp] fullValidate error for ${ doc.getUri }: $e"))(
        scala.concurrent.ExecutionContext.global
      )

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
    // includeText=true なので getText() でテキストを得られるが、didChange で既に最新に保っているので fallback として使う
    val text = Option(params.getText).getOrElse(documents.getOrElse(uri, ""))
    documents(uri) = text
    scala.concurrent
      .Future {
        scala.concurrent.blocking { fullValidate(uri, text) }
      }(scala.concurrent.ExecutionContext.global)
      .failed
      .foreach(e => System.err.println(s"[melt-lsp] fullValidate error for $uri: $e"))(
        scala.concurrent.ExecutionContext.global
      )

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

  /** meltc の構文・セマンティックチェックのみ実行する (高速)。
    * タイプ中のリアルタイムフィードバックに使用する。
    */
  private def fastValidate(uri: String, content: String): Unit =
    client.foreach { c =>
      val filename = uriToFilename(uri)
      val result   = meltc.MeltCompiler.compile(content, filename)
      val diags    =
        result.errors.map(e => makeDiagnostic(e.message, e.line, DiagnosticSeverity.Error)) ++
          result.warnings.map(w => makeDiagnostic(w.message, w.line, DiagnosticSeverity.Warning))
      c.publishDiagnostics(PublishDiagnosticsParams(uri, diags.asJava))
    }

  /** meltc チェック + Metals 型チェックを実行する (低速)。
    * didOpen / didSave 時に非同期で実行する。
    *
    * meltc エラーがある場合は Metals チェックをスキップする。
    * 仮想ファイルも壊れている可能性があり、無意味な型エラーが大量に出るため。
    *
    * didClose と非同期実行の競合を防ぐため、実行開始時と診断送信直前の2箇所で
    * ドキュメントがまだ開かれているかを確認する。
    */
  private def fullValidate(uri: String, content: String): Unit =
    // 非同期実行と didClose の競合対策: client 未接続またはドキュメントが閉じられていたらスキップ。
    client.filter(_ => documents.contains(uri)).foreach { c =>
      val filename = uriToFilename(uri)
      val result   = meltc.MeltCompiler.compile(content, filename)

      val meltcDiags =
        result.errors.map(e => makeDiagnostic(e.message, e.line, DiagnosticSeverity.Error)) ++
          result.warnings.map(w => makeDiagnostic(w.message, w.line, DiagnosticSeverity.Warning))

      val metalsDiags: List[Diagnostic] =
        if result.errors.nonEmpty then Nil
        else
          val vf = VirtualFileGenerator.generate(content)
          metals.diagnosticsForScript(uri, vf)

      // Metals の型チェック (diagnosticsForScript) が長時間ブロックする間に
      // didClose が来る場合があるため、送信直前にも確認する。
      if documents.contains(uri) then
        c.publishDiagnostics(PublishDiagnosticsParams(uri, (meltcDiags ++ metalsDiags).asJava))
    }

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
