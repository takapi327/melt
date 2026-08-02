/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

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

  private[lsp] val workspaceDir: Path = Files.createTempDirectory("melt-lsp-workspace-")
  private val srcDir:            Path = workspaceDir.resolve("src")
  private val bloopDir:          Path = workspaceDir.resolve(".bloop")

  // Backstop: remove the temp workspace on JVM exit in case shutdown() is never
  // called (e.g. the editor kills the process). Removed in shutdown() to avoid
  // accumulating hooks when the bridge is shut down normally.
  private val cleanupHook: Thread = Thread(() =>
    deleteRecursively(workspaceDir); ()
  )
  Runtime.getRuntime.addShutdownHook(cleanupHook)

  // These are written from the `initialize` background future (tryStart) and from
  // shutdown(), and read from completion/definition/hover/diagnostics request
  // threads. @volatile provides the happens-before edge so a request thread never
  // observes a torn or stale view.
  @volatile private var metalsProcess:   Option[Process]               = None
  @volatile private var metalsServer:    Option[LanguageServer]        = None
  @volatile private var listenerFuture:  Option[Future[Void]]          = None
  @volatile private var capturingClient: Option[CapturingMetalsClient] = None

  /** The user's project root, used to recover a real classpath / Scala version
    * for the synthetic Bloop project (see [[createWorkspaceStructure]]). */
  @volatile private var userWorkspaceRoot: Option[Path] = None

  /** Fallback Scala version used only when neither the project's Bloop json files
    * nor its output directories reveal one. */
  private val FallbackScalaVersion = "3.3.4"

  /** Persistent open virtual docs: meltUri → (virtualUri, documentVersion). */
  private val openDocs = ConcurrentHashMap[String, (String, Int)]()

  /** Lock that serialises [[syncVirtualDoc]] calls to prevent TOCTOU races
    * on the document version counter when completions/definitions and diagnostics
    * are requested concurrently for the same .melt file.
    */
  private val syncLock = new Object

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  /** Attempts to find and start a `metals` binary.
    *
    * @param userRoot the user's project root, used to reuse its resolved classpath
    *                 and Scala version for the synthetic Bloop project
    * @return true if Metals was found and successfully initialised
    */
  def startIfAvailable(userRoot: Option[Path] = None): Boolean =
    userWorkspaceRoot = userRoot
    metalsServer.isDefined || findMetalsCommand().exists(tryStart)

  /** Removes the virtual doc entry for a closed .melt file.
    *
    * Called when the editor sends `textDocument/didClose` for a .melt URI.
    * Clears the persistent [[openDocs]] entry so that if the file is reopened
    * later a fresh `didOpen` is sent to Metals rather than a stale `didChange`.
    * Also drops all cached diagnostic state in [[CapturingMetalsClient]] so
    * stale data does not leak into a future re-open.
    */
  def closeDoc(meltUri: String): Unit =
    Option(openDocs.remove(meltUri)).foreach {
      case (virtualUri, _) =>
        capturingClient.foreach(_.dropUri(virtualUri))
    }

  /** Shuts down the Metals subprocess. Safe to call multiple times. */
  def shutdown(): Unit =
    Try { metalsServer.foreach(_.shutdown().get(5, TimeUnit.SECONDS)) }
    Try { metalsServer.foreach(_.exit()) }
    Try { listenerFuture.foreach(_.cancel(true)) }
    Try { metalsProcess.foreach(_.destroyForcibly()) }
    // Stop the scheduler first so no new debounce tasks fire after we clear the maps.
    Try { capturingClient.foreach(_.shutdownScheduler()) }
    Try { capturingClient.foreach(_.dropAll()) }
    openDocs.clear()
    metalsServer    = None
    metalsProcess   = None
    listenerFuture  = None
    capturingClient = None
    // Remove the temp workspace so `melt-lsp-workspace-*` dirs don't accumulate.
    Try { deleteRecursively(workspaceDir) }
    // The dir is gone; drop the JVM-exit backstop so hooks don't accumulate.
    Try { Runtime.getRuntime.removeShutdownHook(cleanupHook) }

  /** Recursively deletes a directory tree, best-effort (ignores individual failures). */
  private def deleteRecursively(path: Path): Unit =
    if Files.isDirectory(path) then
      val stream = Files.list(path)
      try stream.iterator().asScala.foreach(deleteRecursively)
      finally stream.close()
    Try(Files.deleteIfExists(path))

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

  // ── Hover ─────────────────────────────────────────────────────────────────

  /** Requests hover information (type, signature, docs) from Metals for the Scala
    * script section.
    *
    * The hover range, when present, is in virtual .scala coordinates which — thanks
    * to the identity line mapping — are the same as the .melt coordinates, so it can
    * be returned unchanged.
    *
    * @param meltUri   file URI of the .melt document
    * @param vf        [[VirtualFile]] generated from the current .melt source
    * @param line      0-based line in the .melt file where the cursor is
    * @param character 0-based character offset
    * @return the Metals hover, or None when Metals is unavailable or has nothing to show
    */
  def hoverForScript(
    meltUri:   String,
    vf:        VirtualFile,
    line:      Int,
    character: Int
  ): Option[Hover] =
    withSyncedDoc(meltUri, vf) { (server, virtualUri) =>
      val (vLine, vChar) = vf.mapper.meltToVirtual(line, character)
      val params         = HoverParams(TextDocumentIdentifier(virtualUri), Position(vLine, vChar))
      Option(server.getTextDocumentService.hover(params).get(10, TimeUnit.SECONDS))
    }.flatten.filter(hoverHasContent)

  /** True when a hover actually carries text (Metals returns a null/empty hover when
    * there is nothing under the cursor). */
  private def hoverHasContent(h: Hover): Boolean =
    Option(h.getContents).exists { contents =>
      if contents.isRight then Option(contents.getRight).exists(m => Option(m.getValue).exists(_.trim.nonEmpty))
      else Option(contents.getLeft).exists(!_.isEmpty)
    }

  // ── Diagnostics ───────────────────────────────────────────────────────────

  /** Requests Metals to type-check the virtual .scala file and returns diagnostics
    * mapped back to the original .melt URI.
    *
    * The virtual file is kept persistently open (first call uses `didOpen`; subsequent
    * calls use `didChange`). After the content is synced, the method blocks until
    * [[CapturingMetalsClient]] delivers debounced diagnostics or `timeoutSec` elapses.
    *
    * Race-condition handling: the promise is registered via
    * [[CapturingMetalsClient.expectDiagnostics]], which atomically cancels any
    * in-flight debounce task for the same URI before storing the promise.
    * This prevents a debounce from a *previous* compilation completing the
    * promise for the *current* one with stale diagnostics.
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
    // capturingClient and metalsServer share the same lifecycle:
    // both are set together in tryStart() and cleared together in shutdown().
    metalsServer
      .flatMap { server =>
        capturingClient.map { cc =>
          val virtualUri = toVirtualUri(meltUri)
          val promise    = CompletableFuture[List[Diagnostic]]()

          // expectDiagnostics cancels any pending debounce for this URI *before*
          // registering the new promise, so an old compilation's debounce cannot
          // race in and complete the new promise with stale diagnostics.
          cc.expectDiagnostics(virtualUri, promise)

          Try { syncVirtualDoc(server, meltUri, vf) } match
            case scala.util.Failure(_) =>
              cc.dropUri(virtualUri)
              Nil
            case scala.util.Success(_) =>
              try promise.get(timeoutSec, TimeUnit.SECONDS)
              catch
                case _: java.util.concurrent.TimeoutException =>
                  // Remove the orphaned promise so the next call starts clean.
                  cc.dropUri(virtualUri)
                  Nil
                case _: Throwable =>
                  Nil
        }
      }
      .getOrElse(Nil)

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Opens or updates the virtual .scala file in Metals.
    *
    * First call: `textDocument/didOpen` with version 1.
    * Subsequent calls: `textDocument/didChange` with an incremented version.
    * Returns the virtual file URI.
    *
    * The entire read-modify-write on [[openDocs]] is serialised by [[syncLock]]
    * to prevent a TOCTOU race when completions/definitions and diagnostics are
    * requested concurrently for the same URI: without the lock both callers could
    * read the same version number and send two `didChange` notifications with
    * identical version counters, which violates the LSP specification.
    */
  private def syncVirtualDoc(server: LanguageServer, meltUri: String, vf: VirtualFile): String =
    syncLock.synchronized {
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
    }

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

      val cc = new CapturingMetalsClient()
      capturingClient = Some(cc)

      val launcher = LSPLauncher.createClientLauncher(
        cc,
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
        val process = ProcessBuilder(cmd, "--version").redirectErrorStream(true).start()
        if process.waitFor(5, TimeUnit.SECONDS) then process.exitValue() == 0
        else
          // Timed out: destroy the probe so it does not leak, and reject this candidate.
          process.destroyForcibly()
          false
      }.getOrElse(false)
    }

  /** Maps a .melt file URI to the corresponding virtual .scala URI in the temp
    * workspace. The filename includes a hash of the full URI so two `.melt` files
    * that share a basename map to distinct virtual files (see [[MeltVirtualId]]). */
  private def toVirtualUri(meltUri: String): String =
    srcDir.resolve(s"${ MeltVirtualId.fileBaseName(meltUri) }.scala").toUri.toString

  private def createWorkspaceStructure(): Unit =
    Files.createDirectories(srcDir)
    Files.createDirectories(bloopDir)
    val javaHome = System.getProperty("java.home")

    // Reuse the user project's already-resolved Bloop classpath + Scala version so
    // Metals can type-check `melt.runtime.*` and the project's own dependencies.
    // Without this the synthetic project has an empty classpath, and Metals reports
    // "Not found" for essentially every real symbol.
    val resolved =
      userWorkspaceRoot.map(BloopClasspathResolver.resolve).getOrElse(BloopClasspathResolver.Resolved(Nil, None))
    val scalaVersion  = resolved.scalaVersion.getOrElse(FallbackScalaVersion)
    val classpathJson =
      resolved.classpath.map(entry => "\"" + esc(entry) + "\"").mkString(", ")

    val bloopConfig =
      s"""|{
          |  "version": "1.4.0",
          |  "project": {
          |    "name": "melt-virtual",
          |    "directory": "${ esc(workspaceDir.toString) }",
          |    "workspaceDir": "${ esc(workspaceDir.toString) }",
          |    "sources": ["${ esc(srcDir.toString) }"],
          |    "dependencies": [],
          |    "classpath": [$classpathJson],
          |    "out": "${ esc(workspaceDir.resolve("out").toString) }",
          |    "classesDir": "${ esc(workspaceDir.resolve("out/classes").toString) }",
          |    "scala": {
          |      "organization": "org.scala-lang",
          |      "name": "scala3-compiler_3",
          |      "version": "$scalaVersion",
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
  * Metals sends diagnostics multiple times per compilation (initial clear →
  * presentation compiler result → Bloop result). Debouncing ensures that only the
  * final settled result is delivered to [[MetalsBridge.diagnosticsForScript]].
  *
  * ==Promise lifecycle==
  * Callers register interest via [[expectDiagnostics]], which:
  *   1. Cancels any pending debounce task for the URI (prevents a stale debounce
  *      from a previous compilation completing the new promise with old data).
  *   2. Stores the promise in [[pendingPromises]].
  *
  * When a debounce task fires, it removes and completes the stored promise.  If the
  * promise has been replaced or removed (concurrent close/shutdown), the removal
  * returns `null` and nothing is completed.
  *
  * ==Thread safety==
  * [[expectDiagnostics]] (called from the Melt LSP thread) and [[publishDiagnostics]]
  * (called from the Metals LSP4J launcher thread) both modify [[pendingTasks]] and
  * [[pendingPromises]]. They are synchronised on [[lock]] to prevent the following race:
  *   1. `expectDiagnostics` removes the old task from `pendingTasks` and cancels it.
  *   2. (interleave) `publishDiagnostics` schedules a new task and puts it in `pendingTasks`.
  *   3. `expectDiagnostics` puts the new promise in `pendingPromises`.
  *   4. The new task fires and completes the new promise with stale diagnostics.
  *
  * @param debounceMs idle period after the last notification before settling (default 800ms)
  */
private[lsp] class CapturingMetalsClient(debounceMs: Long = 800L) extends LanguageClient:

  /** Latest diagnostics per virtualUri (may be replaced before debounce fires). */
  private val latestDiags = ConcurrentHashMap[String, List[Diagnostic]]()

  /** Promises registered by [[MetalsBridge.diagnosticsForScript]], keyed by virtualUri. */
  private val pendingPromises = ConcurrentHashMap[String, CompletableFuture[List[Diagnostic]]]()

  /** Single-threaded debounce scheduler (daemon thread so it doesn't block JVM exit). */
  private val scheduler: ScheduledExecutorService =
    Executors.newSingleThreadScheduledExecutor { r =>
      val t = Thread(r, "melt-diag-debounce")
      t.setDaemon(true)
      t
    }

  /** Pending debounce tasks per URI. Replaced on each new notification. */
  private val pendingTasks = ConcurrentHashMap[String, ScheduledFuture[?]]()

  /** Guards [[pendingTasks]] and [[pendingPromises]] modifications in both
    * [[expectDiagnostics]] and [[publishDiagnostics]] to prevent the race described
    * in the class scaladoc.
    */
  private val lock = new Object

  /** Registers `promise` as the recipient of the next settled diagnostics for `uri`.
    *
    * Any pending debounce task for `uri` is cancelled first so that a debounce from
    * a previous compilation cannot race in and complete `promise` with stale data.
    */
  def expectDiagnostics(uri: String, promise: CompletableFuture[List[Diagnostic]]): Unit =
    lock.synchronized {
      // Cancel the pending debounce (if any) BEFORE storing the promise.
      // This ensures the old compilation's debounce cannot fire after we register.
      Option(pendingTasks.remove(uri)).foreach(_.cancel(false))
      // Replace any previous (now-cancelled) promise.
      Option(pendingPromises.put(uri, promise)).foreach(_.cancel(false))
    }

  /** Removes all diagnostic state for `uri`.
    *
    * Called when the editor closes a .melt document. Cancels any in-flight
    * debounce task and drops the pending promise (if any) without completing it.
    */
  def dropUri(uri: String): Unit =
    lock.synchronized {
      Option(pendingTasks.remove(uri)).foreach(_.cancel(false))
      Option(pendingPromises.remove(uri)).foreach(_.cancel(false))
      latestDiags.remove(uri)
    }

  /** Cancels all pending promises and debounce tasks. Called on shutdown. */
  def dropAll(): Unit =
    lock.synchronized {
      pendingTasks.values().forEach(_.cancel(false))
      pendingTasks.clear()
      pendingPromises.values().forEach(_.cancel(false))
      pendingPromises.clear()
      latestDiags.clear()
    }

  override def telemetryEvent(obj: Any):                        Unit                                 = ()
  override def showMessage(p:      MessageParams):              Unit                                 = ()
  override def showMessageRequest(p: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
    CompletableFuture.completedFuture(null)
  override def logMessage(p: MessageParams): Unit = ()

  override def publishDiagnostics(p: PublishDiagnosticsParams): Unit =
    val uri   = p.getUri
    val diags = p.getDiagnostics.asScala.toList
    latestDiags.put(uri, diags)

    // Cancel the previous debounce task (if any) and schedule a new one.
    // Wrapped in lock to prevent a race with expectDiagnostics (see class scaladoc).
    // Wrapped in try/catch to handle RejectedExecutionException after shutdownNow().
    lock.synchronized {
      Option(pendingTasks.remove(uri)).foreach(_.cancel(false))
      try
        val task: ScheduledFuture[?] = scheduler.schedule(
          new Runnable:
            def run(): Unit =
              val settled = latestDiags.getOrDefault(uri, Nil)
              // Remove the promise atomically; if it was already replaced or dropped,
              // remove returns null and nothing is completed.
              Option(pendingPromises.remove(uri)).foreach(_.complete(settled))
          ,
          debounceMs,
          TimeUnit.MILLISECONDS
        )
        pendingTasks.put(uri, task)
      catch case _: java.util.concurrent.RejectedExecutionException => () // scheduler already shut down
    }

  /** Stops the debounce scheduler. Called from [[MetalsBridge.shutdown]]. */
  def shutdownScheduler(): Unit =
    scheduler.shutdownNow()
    ()
