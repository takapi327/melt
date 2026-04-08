/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.nio.file.{ Files, Path }
import java.util.concurrent.{ CompletableFuture, Future, TimeUnit }

import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.*

/** Manages a Metals subprocess for Scala code intelligence in .melt script sections.
  *
  * MetalsBridge starts a `metals` process, initializes it over an in-process LSP4J
  * client/server pair, writes virtual .scala files to a temp workspace backed by a
  * minimal Bloop project, and forwards completion requests with position translation
  * via [[PositionMapper]].
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
  * ==Lifecycle==
  * Call [[startIfAvailable]] once (e.g. in [[MeltLanguageServer.initialize]]) and
  * [[shutdown]] when the server exits.  [[completionsForScript]] can then be called
  * for any number of documents.
  */
class MetalsBridge:

  private val workspaceDir: Path = Files.createTempDirectory("melt-lsp-workspace-")
  private val srcDir:       Path = workspaceDir.resolve("src")
  private val bloopDir:     Path = workspaceDir.resolve(".bloop")

  private var metalsProcess:  Option[Process]        = None
  private var metalsServer:   Option[LanguageServer] = None
  private var listenerFuture: Option[Future[Void]]   = None

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

  /** Shuts down the Metals subprocess. Safe to call multiple times. */
  def shutdown(): Unit =
    Try { metalsServer.foreach(_.shutdown().get(5, TimeUnit.SECONDS)) }
    Try { metalsServer.foreach(_.exit()) }
    Try { listenerFuture.foreach(_.cancel(true)) }
    Try { metalsProcess.foreach(_.destroyForcibly()) }
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
    withVirtualDoc(meltUri, vf) { (server, virtualUri) =>
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
    withVirtualDoc(meltUri, vf) { (server, virtualUri) =>
      val (vLine, vChar) = vf.mapper.meltToVirtual(line, character)
      val params         = DefinitionParams(
        TextDocumentIdentifier(virtualUri),
        Position(vLine, vChar)
      )
      val result    = server.getTextDocumentService.definition(params).get(10, TimeUnit.SECONDS)
      val locations =
        if result.isLeft then result.getLeft.asScala.toList
        else result.getRight.asScala.map(ll => Location(ll.getTargetUri, ll.getTargetSelectionRange)).toList
      // Translate virtual .scala URIs back to the .melt URI
      locations.map { loc =>
        if loc.getUri == virtualUri then Location(meltUri, loc.getRange)
        else loc
      }
    }.getOrElse(Nil)

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Opens the virtual .scala doc in Metals, runs [body], then closes it.
    * Returns None if Metals is unavailable or on any exception.
    */
  private def withVirtualDoc[A](
    meltUri: String,
    vf:      VirtualFile
  )(body: (LanguageServer, String) => A): Option[A] =
    metalsServer.flatMap { server =>
      Try {
        val virtualUri = toVirtualUri(meltUri)
        val docItem    = TextDocumentItem(virtualUri, "scala", 1, vf.content)
        server.getTextDocumentService.didOpen(DidOpenTextDocumentParams(docItem))
        val result = body(server, virtualUri)
        server.getTextDocumentService.didClose(
          DidCloseTextDocumentParams(TextDocumentIdentifier(virtualUri))
        )
        result
      }.toOption
    }

  private def tryStart(cmd: String): Boolean =
    Try {
      createWorkspaceStructure()
      val process = ProcessBuilder(cmd)
        .directory(workspaceDir.toFile)
        .start()
      metalsProcess = Some(process)

      val client   = new NoOpMetalsClient
      val launcher = LSPLauncher.createClientLauncher(
        client,
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

/** No-op LanguageClient used as the client side of the Metals subprocess connection.
  * Metals occasionally sends notifications (showMessage, publishDiagnostics) that
  * the bridge discards since they are internal to the virtual workspace.
  */
private class NoOpMetalsClient extends LanguageClient:
  override def telemetryEvent(obj:   Any):                      Unit                                 = ()
  override def publishDiagnostics(p: PublishDiagnosticsParams): Unit                                 = ()
  override def showMessage(p:        MessageParams):            Unit                                 = ()
  override def showMessageRequest(p: ShowMessageRequestParams): CompletableFuture[MessageActionItem] =
    CompletableFuture.completedFuture(null)
  override def logMessage(p: MessageParams): Unit = ()
