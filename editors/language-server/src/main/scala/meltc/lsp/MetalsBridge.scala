/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.nio.file.{ Files, Path }
import java.util.concurrent.{
  CompletableFuture,
  ConcurrentHashMap,
  Executors,
  Future,
  ScheduledExecutorService,
  ScheduledFuture,
  TimeUnit
}

import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.*

/** Manages a Metals subprocess for Scala code intelligence in .melt script sections.
  *
  * MetalsBridge starts a `metals` process, initializes it over an in-process LSP4J
  * client/server pair, writes virtual .scala files to a temp workspace backed by a
  * minimal Bloop project, and forwards completion/definition/diagnostic requests with
  * position translation via [[PositionMapper]].
  *
  * All public methods degrade gracefully: if Metals is not on PATH, if the process
  * fails to start, or if a request times out, an empty list is returned rather than
  * propagating an exception.
  *
  * ==Workspace layout==
  * {{{
  *   <tmp>/melt-lsp-workspace-<random>/
  *     .bloop/
  *       melt-virtual.json   ← minimal Bloop config so Metals recognises the project
  *     src/
  *       <ComponentName>.scala  ← virtual .scala for each open .melt document
  * }}}
  *
  * ==Virtual file lifecycle==
  * Virtual files are kept persistently open in Metals rather than open/close per request.
  * The first call opens the file with `textDocument/didOpen`; subsequent calls update the
  * content with `textDocument/didChange`. This preserves Metals' compilation cache and
  * avoids triggering a full recompile on every request.
  *
  * ==Diagnostics==
  * Metals sends `publishDiagnostics` multiple times per compilation (clear → presentation
  * compiler → Bloop). [[CapturingMetalsClient]] debounces these notifications: after 800ms
  * of silence, the last received diagnostic list is considered final and delivered to any
  * waiting [[diagnosticsForScript]] call via a [[CompletableFuture]].
  *
  * ==Lifecycle==
  * Call [[startIfAvailable]] once (e.g. in [[MeltLanguageServer.initialize]]) and
  * [[shutdown]] when the server exits.
  */
class MetalsBridge:

  private val workspaceDir: Path = Files.createTempDirectory("melt-lsp-workspace-")
  private val srcDir:       Path = workspaceDir.resolve("src")
  private val bloopDir:     Path = workspaceDir.resolve(".bloop")

  private var metalsProcess:   Option[Process]        = None
  private var metalsServer:    Option[LanguageServer] = None
  private var listenerFuture:  Option[Future[Void]]   = None
  private var capturingClient: CapturingMetalsClient  = scala.compiletime.uninitialized

  /** Persistent open virtual docs: meltUri → (virtualUri, documentVersion). */
  private val openDocs = ConcurrentHashMap[String, (String, Int)]()

  /** Pending diagnostics futures keyed by virtualUri.
    * Registered by [[diagnosticsForScript]] before triggering compilation,
    * completed by [[CapturingMetalsClient]] when the debounce settles.
    */
  private val pendingDiagFutures = ConcurrentHashMap[String, CompletableFuture[List[Diagnostic]]]()

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  /** Attempts to find and start a `metals` binary.
    *
    * @return true if Metals was found and successfully initialised
    */
  def startIfAvailable(): Boolean =
    if metalsServer.isDefined then return true
    findMetalsCommand() match
      case None      => false
      case Some(cmd) => tryStart(cmd)

  /** Removes the virtual doc entry for a closed .melt file.
    *
    * Called when the editor sends `textDocument/didClose` for a .melt URI.
    * Clears the persistent [[openDocs]] entry so that if the file is reopened
    * later a fresh `didOpen` is sent to Metals rather than a stale `didChange`.
    */
  def closeDoc(meltUri: String): Unit =
    openDocs.remove(meltUri)
    ()

  /** Shuts down the Metals subprocess. Safe to call multiple times. */
  def shutdown(): Unit =
    Try { metalsServer.foreach(_.shutdown().get(5, TimeUnit.SECONDS)) }
    Try { metalsServer.foreach(_.exit()) }
    Try { listenerFuture.foreach(_.cancel(true)) }
    Try { metalsProcess.foreach(_.destroyForcibly()) }
    Try { if capturingClient != null then capturingClient.shutdownScheduler() }
    openDocs.clear()
    pendingDiagFutures.clear()
    metalsServer   = None
    metalsProcess  = None
    listenerFuture = None

  // ── Completions ───────────────────────────────────────────────────────────

  /** Requests completion items from Metals for the Scala script section.
    *
    * @param meltUri   file URI of the .melt document
    * @param vf        [[VirtualFile]] generated from the current .melt source
    * @param line      0-based line in the .melt file at which completions are requested
    * @param character 0-based character offset
    * @return completion items from Metals, or empty list on any failure
    */
  def completionsForScript(
    meltUri:   String,
    vf:        VirtualFile,
    line:      Int,
    character: Int
  ): List[CompletionItem] =
    withSyncedDoc(meltUri, vf) { (server, virtualUri) =>
      val (vLine, vChar) = vf.mapper.meltToVirtual(line, character)
      val params         = CompletionParams(
        TextDocumentIdentifier(virtualUri),
        Position(vLine, vChar)
      )
      val result = server.getTextDocumentService.completion(params).get(10, TimeUnit.SECONDS)
      if result.isLeft then result.getLeft.asScala.toList
      else result.getRight.getItems.asScala.toList
    }.getOrElse(Nil)

  // ── Definition ────────────────────────────────────────────────────────────

  /** Requests definition locations from Metals for the Scala script section.
    *
    * Metals returns [[Location]]s pointing to the virtual .scala file; this method
    * translates those URIs back to the original .melt URI so the editor navigates
    * to the correct file.  Line numbers are identical (identity mapping).
    *
    * @param meltUri   file URI of the .melt document
    * @param vf        [[VirtualFile]] generated from the current .melt source
    * @param line      0-based line in the .melt file where the cursor is
    * @param character 0-based character offset
    * @return definition locations in the .melt file, or empty list on any failure
    */
  def definitionForScript(
    meltUri:   String,
    vf:        VirtualFile,
    line:      Int,
    character: Int
  ): List[Location] =
    withSyncedDoc(meltUri, vf) { (server, virtualUri) =>
      val (vLine, vChar) = vf.mapper.meltToVirtual(line, character)
      val params         = DefinitionParams(
        TextDocumentIdentifier(virtualUri),
        Position(vLine, vChar)
      )
      val result    = server.getTextDocumentService.definition(params).get(10, TimeUnit.SECONDS)
      val locations =
        if result.isLeft then result.getLeft.asScala.toList
        else result.getRight.asScala.map(ll => Location(ll.getTargetUri, ll.getTargetSelectionRange)).toList
      locations.map { loc =>
        if loc.getUri == virtualUri then Location(meltUri, loc.getRange)
        else loc
      }
    }.getOrElse(Nil)

  // ── Diagnostics ───────────────────────────────────────────────────────────

  /** Requests Metals to type-check the virtual .scala file and returns diagnostics
    * mapped back to the original .melt URI.
    *
    * The virtual file is kept persistently open (first call uses `didOpen`; subsequent
    * calls use `didChange`). After the content is synced, the method blocks until
    * [[CapturingMetalsClient]] delivers debounced diagnostics or `timeoutSec` elapses.
    *
    * The pending future is registered *before* syncing the virtual doc to avoid a race
    * where Metals sends diagnostics (debounce fires) before the future is registered.
    *
    * @param meltUri    file URI of the .melt document
    * @param vf         [[VirtualFile]] generated from the current .melt source
    * @param timeoutSec max seconds to wait for diagnostics to settle (default 30)
    * @return diagnostics pointing to the .melt URI, or empty list on timeout/failure
    */
  def diagnosticsForScript(
    meltUri:    String,
    vf:         VirtualFile,
    timeoutSec: Int = 30
  ): List[Diagnostic] =
    metalsServer
      .map { server =>
        Try {
          val virtualUri = toVirtualUri(meltUri)

          // Cancel any existing promise for the same URI (e.g. from a concurrent save)
          // to prevent it blocking for the full timeout after being overwritten.
          Option(pendingDiagFutures.get(virtualUri)).foreach(_.cancel(false))

          // Register the new promise BEFORE syncing to avoid the race where the debounce
          // fires (from a previous compilation) before we start waiting.
          val promise = CompletableFuture[List[Diagnostic]]()
          pendingDiagFutures.put(virtualUri, promise)

          syncVirtualDoc(server, meltUri, vf)

          promise.get(timeoutSec, TimeUnit.SECONDS)
        }.getOrElse(Nil)
      }
      .getOrElse(Nil)

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Opens or updates the virtual .scala file in Metals.
    *
    * First call: `textDocument/didOpen` with version 1.
    * Subsequent calls: `textDocument/didChange` with an incremented version.
    * Returns the virtual file URI.
    */
  private def syncVirtualDoc(server: LanguageServer, meltUri: String, vf: VirtualFile): String =
    val virtualUri = toVirtualUri(meltUri)
    Option(openDocs.get(meltUri)) match
      case None =>
        val docItem = TextDocumentItem(virtualUri, "scala", 1, vf.content)
        server.getTextDocumentService.didOpen(DidOpenTextDocumentParams(docItem))
        openDocs.put(meltUri, (virtualUri, 1))
      case Some((_, ver)) =>
        val nextVer      = ver + 1
        val change       = TextDocumentContentChangeEvent(vf.content)
        val changeParams = DidChangeTextDocumentParams(
          VersionedTextDocumentIdentifier(virtualUri, nextVer),
          List(change).asJava
        )
        server.getTextDocumentService.didChange(changeParams)
        openDocs.put(meltUri, (virtualUri, nextVer))
    virtualUri

  /** Syncs the virtual .scala doc and runs [body] with (server, virtualUri).
    * Returns None if Metals is unavailable or on any exception.
    */
  private def withSyncedDoc[A](
    meltUri: String,
    vf:      VirtualFile
  )(body: (LanguageServer, String) => A): Option[A] =
    metalsServer.flatMap { server =>
      Try {
        val virtualUri = syncVirtualDoc(server, meltUri, vf)
        body(server, virtualUri)
      }.toOption
    }

  private def tryStart(cmd: String): Boolean =
    Try {
      createWorkspaceStructure()
      val process = ProcessBuilder(cmd)
        .directory(workspaceDir.toFile)
        .start()
      metalsProcess = Some(process)

      capturingClient = new CapturingMetalsClient(
        onDiagnosticsSettled = (virtualUri, diags) => {
          Option(pendingDiagFutures.remove(virtualUri)).foreach(_.complete(diags))
        }
      )

      val launcher = LSPLauncher.createClientLauncher(
        capturingClient,
        process.getInputStream,
        process.getOutputStream
      )
      val server = launcher.getRemoteProxy
      listenerFuture = Some(launcher.startListening())

      val initParams = InitializeParams()
      initParams.setRootUri(workspaceDir.toUri.toString)
      initParams.setCapabilities(buildClientCapabilities())
      server.initialize(initParams).get(30, TimeUnit.SECONDS)
      server.initialized(InitializedParams())

      metalsServer = Some(server)
      true
    }.recover { _ =>
      shutdown()
      false
    }.getOrElse(false)

  /** Returns the path to the first `metals` binary found on common install locations. */
  private def findMetalsCommand(): Option[String] =
    val home       = System.getProperty("user.home")
    val candidates = List(
      "metals",
      s"$home/.local/share/coursier/bin/metals",
      s"$home/.coursier/bin/metals",
      s"$home/.metals/metals",
      "/usr/local/bin/metals",
      "/opt/homebrew/bin/metals"
    )
    candidates.find { cmd =>
      Try {
        ProcessBuilder(cmd, "--version")
          .redirectErrorStream(true)
          .start()
          .waitFor(5, TimeUnit.SECONDS)
        true
      }.getOrElse(false)
    }

  /** Maps a .melt file URI to the corresponding virtual .scala URI in the temp workspace. */
  private def toVirtualUri(meltUri: String): String =
    val basename = meltUri.replaceAll(".*[/\\\\]", "").stripSuffix(".melt")
    srcDir.resolve(s"$basename.scala").toUri.toString

  private def createWorkspaceStructure(): Unit =
    Files.createDirectories(srcDir)
    Files.createDirectories(bloopDir)
    val javaHome = System.getProperty("java.home")
    // Minimal Bloop config: Metals needs a project definition to start managing
    // the workspace. The classpath is intentionally left empty here — Metals
    // will use Coursier to resolve the Scala standard library on first use.
    val bloopConfig =
      s"""|{
          |  "version": "1.4.0",
          |  "project": {
          |    "name": "melt-virtual",
          |    "directory": "${ esc(workspaceDir.toString) }",
          |    "workspaceDir": "${ esc(workspaceDir.toString) }",
          |    "sources": ["${ esc(srcDir.toString) }"],
          |    "dependencies": [],
          |    "classpath": [],
          |    "out": "${ esc(workspaceDir.resolve("out").toString) }",
          |    "classesDir": "${ esc(workspaceDir.resolve("out/classes").toString) }",
          |    "scala": {
          |      "organization": "org.scala-lang",
          |      "name": "scala3-compiler_3",
          |      "version": "3.6.4",
          |      "options": [],
          |      "jars": [],
          |      "analysis": "${ esc(workspaceDir.resolve("out/analysis.bin").toString) }",
          |      "setup": {
          |        "order": "mixed",
          |        "addLibraryToBootClasspath": true,
          |        "addCompilerToClasspath": false,
          |        "addExtraJarsToClasspath": false,
          |        "manageBootClasspath": true,
          |        "filterLibraryFromClasspath": false
          |      }
          |    },
          |    "java": {
          |      "options": [],
          |      "home": "${ esc(javaHome) }"
          |    },
          |    "test": {
          |      "frameworks": [],
          |      "options": { "excludes": [], "arguments": [] }
          |    },
          |    "platform": {
          |      "name": "jvm",
          |      "config": {
          |        "home": "${ esc(javaHome) }",
          |        "options": []
          |      },
          |      "mainClass": []
          |    },
          |    "resolution": { "modules": [] }
          |  }
          |}
          |""".stripMargin
    Files.writeString(bloopDir.resolve("melt-virtual.json"), bloopConfig)

  /** Escapes backslashes in JSON strings (for Windows paths). */
  private def esc(path: String): String = path.replace("\\", "\\\\")

  private def buildClientCapabilities(): ClientCapabilities =
    val completionItemCaps = CompletionItemCapabilities()
    completionItemCaps.setSnippetSupport(true)
    val completionCaps = CompletionCapabilities()
    completionCaps.setCompletionItem(completionItemCaps)
    val textDocCaps = TextDocumentClientCapabilities()
    textDocCaps.setCompletion(completionCaps)
    val caps = ClientCapabilities()
    caps.setTextDocument(textDocCaps)
    caps

/** Captures `publishDiagnostics` notifications from Metals and debounces them.
  *
  * Metals sends diagnostics multiple times per compilation (initial clear → presentation
  * compiler result → Bloop result). Debouncing ensures that only the final settled
  * result is delivered to [[MetalsBridge.diagnosticsForScript]].
  *
  * After each `publishDiagnostics` call, any pending debounce task for that URI is
  * cancelled and a new one is scheduled for `debounceMs` milliseconds later. When the
  * task fires, `onDiagnosticsSettled` is called with the latest cached diagnostics.
  *
  * @param onDiagnosticsSettled callback invoked with (virtualUri, diagnostics) when settled
  * @param debounceMs           idle period after the last notification before settling (default 800ms)
  */
private class CapturingMetalsClient(
  onDiagnosticsSettled: (String, List[Diagnostic]) => Unit,
  debounceMs:           Long = 800L
) extends LanguageClient:

  /** Latest diagnostics per virtualUri (may be replaced before debounce fires). */
  private val latestDiags = ConcurrentHashMap[String, List[Diagnostic]]()

  /** Single-threaded debounce scheduler (daemon thread so it doesn't block JVM exit). */
  private val scheduler: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { r =>
      val t = Thread(r, "melt-diag-debounce")
      t.setDaemon(true)
      t
    }

  /** Pending debounce tasks per URI. Replaced on each new notification. */
  private val pendingTasks = ConcurrentHashMap[String, ScheduledFuture[?]]()

  override def telemetryEvent(obj: Any):                        Unit                                 = ()
  override def showMessage(p:      MessageParams):              Unit                                 = ()
  override def showMessageRequest(p: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
    CompletableFuture.completedFuture(null)
  override def logMessage(p: MessageParams): Unit = ()

  override def publishDiagnostics(p: PublishDiagnosticsParams): Unit =
    val uri   = p.getUri
    val diags = p.getDiagnostics.asScala.toList
    latestDiags.put(uri, diags)

    // Cancel the previous debounce task (if any) and schedule a new one
    Option(pendingTasks.remove(uri)).foreach(_.cancel(false))
    val task: ScheduledFuture[?] = scheduler.schedule(
      new Runnable:
        def run(): Unit = onDiagnosticsSettled(uri, latestDiags.getOrDefault(uri, Nil))
      ,
      debounceMs,
      TimeUnit.MILLISECONDS
    )
    pendingTasks.put(uri, task)

  /** Stops the debounce scheduler. Called from [[MetalsBridge.shutdown]]. */
  def shutdownScheduler(): Unit =
    scheduler.shutdownNow()
    ()
