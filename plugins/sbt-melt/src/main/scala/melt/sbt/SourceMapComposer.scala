/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import java.io.File

import melt.sbt.MiniJson.*

/** Composes the Scala.js linker source map (`.js` → `.scala`) with each
  * melt-generated file's embedded MELT map (`.scala` → `.melt`) so browser
  * debuggers and stack traces resolve to the original `.melt` sources.
  *
  * A linker segment that points into a melt-generated `.scala` is rewritten to
  * point at the corresponding `.melt` source (line/column via
  * [[MeltGeneratedSource.mapPosition]]); segments pointing at hand-written Scala
  * are left untouched, so debugging those still works.
  */
object SourceMapComposer:

  /** @param linkerMapJson raw contents of the linker's `.js.map`
    * @param metaFor       resolves a `sources` entry to its MELT metadata when it
    *                      is a melt-generated `.scala` (else `None`)
    * @param contentFor    optional `.melt` source text for `sourcesContent`
    * @return the composed `.js.map` JSON with `.melt` sources spliced in
    */
  def compose(
    linkerMapJson: String,
    metaFor:       String => Option[MeltGeneratedSource.Meta],
    contentFor:    String => Option[String] = _ => None
  ): String =
    val obj      = MiniJson.parse(linkerMapJson).asObject
    val sources  = obj.field("sources").asArray.map(_.asString)
    val mappings = obj.field("mappings").asString

    // A meta with no V3 entries (legacy output) can't remap anything — ignore it
    // so we never splice in a dead `.melt` source.
    val metas: Vector[Option[MeltGeneratedSource.Meta]] =
      sources.map(metaFor).map(_.filter(_.lines.nonEmpty))

    // Nothing melt-generated, or the map was already composed (a fresh linker map
    // never lists a `.melt` source) → return it unchanged.
    if metas.forall(_.isEmpty) || sources.exists(_.endsWith(".melt")) then linkerMapJson
    else
      val meltPaths = metas.flatten.map(_.sourcePath).distinct
      val meltIndexOf: Map[String, Int] =
        meltPaths.zipWithIndex.map { case (p, i) => p -> (sources.length + i) }.toMap
      val newSources = sources ++ meltPaths

      val decoded  = SourceMapV3Codec.decode(mappings)
      val remapped = decoded.map(_.map { seg =>
        seg.source match
          case Some(ref) if ref.srcIndex >= 0 && ref.srcIndex < metas.length =>
            metas(ref.srcIndex) match
              case Some(meta) =>
                MeltGeneratedSource.mapPosition(meta, ref.srcLine + 1) match
                  case Some((meltLine, meltCol)) =>
                    seg.copy(source =
                      Some(
                        SourceMapV3Codec.SourceRef(
                          srcIndex  = meltIndexOf(meta.sourcePath),
                          srcLine   = meltLine - 1,
                          srcCol    = meltCol - 1,
                          nameIndex = None
                        )
                      )
                    )
                  case None => seg
              case None => seg
          case _ => seg
      })
      val newMappings = SourceMapV3Codec.encode(remapped)

      var result = obj
        .withField("sources", JArr(newSources.map(JStr(_))))
        .withField("mappings", JStr(newMappings))

      obj.get("sourcesContent").collect {
        case JArr(existing) =>
          val appended = meltPaths.map(p => contentFor(p).map(JStr(_)).getOrElse(JNull))
          result = result.withField("sourcesContent", JArr(existing ++ appended))
      }

      MiniJson.render(result)

  /** Composes a single linker `.js.map` file in place, resolving its `sources`
    * entries to generated `.scala` files on disk (relative entries are resolved
    * against the map's own directory). Returns `true` when the file changed.
    *
    * An empty or unparseable map is left alone rather than raised: source maps are a
    * debugging aid, so a stale artifact or a file still being written by a concurrent
    * linker run must not abort the task.
    */
  def composeFile(jsMapFile: File): Boolean =
    if !jsMapFile.exists() then false
    else
      val original = readFile(jsMapFile)
      val baseDir  = jsMapFile.getParentFile

      def metaFor(src: String): Option[MeltGeneratedSource.Meta] =
        resolveSource(baseDir, src).flatMap(MeltGeneratedSource.read)

      def contentFor(meltPath: String): Option[String] =
        val f = new File(meltPath)
        if f.exists() then Some(readFile(f)) else None

      if original.trim.isEmpty then false
      else
        val composed =
          try compose(original, metaFor, contentFor)
          catch case _: Exception => original
        if composed == original then false
        else
          writeFile(jsMapFile, composed)
          true

  /** Composes every `*.js.map` under `distDir` (non-recursive). Returns the files
    * that were rewritten. */
  def composeDirectory(distDir: File): Seq[File] =
    val maps =
      Option(distDir.listFiles((_, name) => name.endsWith(".js.map")))
        .getOrElse(Array.empty[File])
    maps.toSeq.filter(composeFile)

  private def resolveSource(baseDir: File, src: String): Option[File] =
    val cleaned  = if src.startsWith("file://") then src.stripPrefix("file://") else src
    val f        = new File(cleaned)
    val resolved = if f.isAbsolute then f else new File(baseDir, cleaned)
    if resolved.exists() then Some(resolved) else None

  private def readFile(f: File): String =
    val src = scala.io.Source.fromFile(f, "UTF-8")
    try src.mkString
    finally src.close()

  private def writeFile(f: File, content: String): Unit =
    val w = new java.io.PrintWriter(f, "UTF-8")
    try w.write(content)
    finally w.close()
