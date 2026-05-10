/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import java.nio.file.{ Files, Path }
import java.util.stream.Collectors

import scala.jdk.CollectionConverters.*

import org.eclipse.lsp4j.*

/** Provides path completions for `import "..."` string literal imports in
  * `.melt` script sections.
  *
  * When the cursor is inside the double-quoted string of an `import "..."` line,
  * this provider returns file paths from a pre-built in-memory index.
  *
  * == Usage ==
  *
  * Call [[buildIndex]] once at server startup (e.g. in `initialized()`), store the
  * result in a [[scala.collection.concurrent.TrieMap]], and pass it to
  * [[completionsFor]] on each completion request.  Use [[handleFileEvent]] to keep
  * the index up-to-date when the client reports `workspace/didChangeWatchedFiles`.
  *
  * Supported extensions: `.css`, `.scss`, `.less`, `.sass`, `.js`, `.mjs`,
  * `.ts`, `.jsx`, `.tsx`
  *
  * Example: typing `import "/sty` triggers completions like `/styles/global.css`.
  */
object ImportPathCompletionProvider:

  /** Extensions recognised as importable file types. */
  private val SupportedExtensions =
    Set(".css", ".scss", ".less", ".sass", ".js", ".mjs", ".ts", ".jsx", ".tsx")

  /** Directory names that are excluded from workspace file scanning. */
  private val ExcludedDirs = Set("node_modules", ".git", "target", ".bsp", ".metals", ".idea")

  /** Regex matching a line where the cursor is inside the double-quoted string
    * of an `import "..."` statement.
    *
    * Matches: `  import "/sty` (cursor at end — closing quote may or may not exist).
    * The capture group extracts the partial path typed so far.
    */
  private val ImportPrefixRe = """^\s*import\s+"([^"]*)$""".r

  /** Returns the already-typed path prefix when the text before the cursor is
    * the start of a string literal import statement, or `None` otherwise.
    */
  def detectImportPrefix(lineText: String, charPos: Int): Option[String] =
    val before = lineText.take(charPos)
    ImportPrefixRe.findFirstMatchIn(before).map(_.group(1))

  /** Builds a full file index by scanning `root` once.
    *
    * The returned map has absolute paths (e.g. `"/styles/global.css"`) as keys
    * and ready-to-use [[CompletionItem]]s as values.  Only files with
    * [[SupportedExtensions]] that are not inside [[ExcludedDirs]] are included.
    *
    * Intended to be called once during `initialized()` and stored for reuse.
    */
  def buildIndex(root: Path): Map[String, CompletionItem] =
    if !Files.isDirectory(root) then Map.empty
    else
      try
        Files
          .walk(root)
          .collect(Collectors.toList[Path])
          .asScala
          .toList
          .filter(p => Files.isRegularFile(p) && hasSupportedExtension(p) && !isExcluded(p))
          .map { p =>
            val absPath = toAbsPath(root, p)
            absPath -> makeItem(absPath, p)
          }
          .toMap
      catch case _: Exception => Map.empty

  /** Returns completion items from a pre-built index whose paths start with `prefix`.
    *
    * This is the primary entry point for the server: pass the [[scala.collection.concurrent.TrieMap]]
    * built by [[buildIndex]] and kept up-to-date via [[handleFileEvent]].
    */
  def completionsFor(
    prefix: String,
    index:  scala.collection.Map[String, CompletionItem]
  ): List[CompletionItem] =
    index.collect { case (path, item) if path.startsWith(prefix) => item }
      .toList
      .sortBy(_.getLabel)

  /** Convenience overload that builds a temporary index on the fly.
    *
    * Used by unit tests.  For production use, prefer the pre-built index overload.
    */
  def completionsFor(prefix: String, workspaceRoot: Option[Path]): List[CompletionItem] =
    workspaceRoot match
      case None       => Nil
      case Some(root) => completionsFor(prefix, buildIndex(root))

  /** Updates `index` in place to reflect a single [[FileEvent]].
    *
    * - `Created` / `Changed`: inserts or replaces the entry for the file.
    * - `Deleted`: removes the entry for the file.
    *
    * Files outside [[SupportedExtensions]] or inside [[ExcludedDirs]] are ignored.
    * Any exception (e.g. invalid URI, path outside root) is silently swallowed so
    * that a malformed notification cannot crash the language server.
    */
  def handleFileEvent(
    root:  Path,
    event: FileEvent,
    index: scala.collection.concurrent.Map[String, CompletionItem]
  ): Unit =
    try
      val path = java.nio.file.Paths.get(java.net.URI.create(event.getUri))
      if hasSupportedExtension(path) && !isExcluded(path) then
        val absPath = toAbsPath(root, path)
        if event.getType == FileChangeType.Deleted then index.remove(absPath)
        else if Files.isRegularFile(path) then index.update(absPath, makeItem(absPath, path))
    catch case _: Exception => ()

  private def toAbsPath(root: Path, file: Path): String =
    "/" + root.relativize(file).toString.replace('\\', '/')

  private def isExcluded(path: Path): Boolean =
    path.iterator.asScala.exists(seg => ExcludedDirs.contains(seg.toString))

  private def hasSupportedExtension(path: Path): Boolean =
    val name = path.getFileName.toString
    val dot  = name.lastIndexOf('.')
    if dot < 0 then false
    else SupportedExtensions.contains(name.substring(dot))

  private def makeItem(absPath: String, fsPath: Path): CompletionItem =
    val item  = CompletionItem(absPath)
    val name  = fsPath.getFileName.toString
    val dot   = name.lastIndexOf('.')
    val ext   = if dot >= 0 then name.substring(dot) else ""
    val isJs  = Set(".js", ".mjs", ".ts", ".jsx", ".tsx").contains(ext)
    val kind  = if isJs then CompletionItemKind.Module else CompletionItemKind.File
    item.setKind(kind)
    item.setDetail(if isJs then "JS import" else "CSS import")
    item.setInsertText(absPath)
    item.setInsertTextFormat(InsertTextFormat.PlainText)
    item
