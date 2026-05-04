/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.Optional

import xsbti.Position

/** Provides an `xsbti.Position => Option[xsbti.Position]` mapper that
  * remaps scalac error positions from generated `.scala` files back to
  * the original `.melt` source files.
  *
  * The mapper is registered in sbt via:
  * {{{
  * Compile / sourcePositionMappers += { pos => MeltSourceMap.positionMapper(pos) }
  * }}}
  *
  * Implementation notes (Scala 2.12 / sbt plugin constraints):
  *   - `java.util.Optional` is used directly (no `.toOption` available)
  *   - Anonymous class syntax `new xsbti.Position { ... }` (not `new Trait:`)
  */
object MeltSourceMap {

  /** Cache keyed by (file, lastModified) to avoid re-reading the same generated
    * `.scala` file for every error reported by scalac in a single compilation.
    * The lastModified timestamp invalidates stale entries across recompilations.
    */
  private val cache =
    new ConcurrentHashMap[(File, Long), Option[MeltGeneratedSource.Meta]]()

  /** Maps a scalac position in a generated `.scala` file to the corresponding
    * position in the original `.melt` file.
    *
    * Returns `None` when:
    *   - The source file is not a `.scala` file (already mapped or other source)
    *   - No `-- MELT GENERATED --` block is present in the generated file
    *   - The generated line is before the first LINES entry
    */
  val positionMapper: Position => Option[Position] = { pos =>
    // Only remap positions that come from a .scala file.
    // The sourceFile Optional may be absent for synthetic positions.
    if (!pos.sourceFile().isPresent) None
    else {
      val genFile = pos.sourceFile().get()
      if (!genFile.getName.endsWith(".scala")) None
      else {
        val cacheKey = (genFile, genFile.lastModified())
        val meta     = {
          val cached = cache.get(cacheKey)
          if (cached != null) cached
          else {
            val result = MeltGeneratedSource.read(genFile)
            cache.put(cacheKey, result)
            result
          }
        }
        meta match {
          case None       => None
          case Some(meta) =>
            val meltFile = new File(meta.sourcePath)
            if (!meltFile.exists()) None
            else if (!pos.line().isPresent) None
            else {
              val genLine = pos.line().get().intValue()
              MeltGeneratedSource.mapLine(meta, genLine) match {
                case None          => None
                case Some(srcLine) =>
                  Some(remappedPosition(meltFile, srcLine))
              }
            }
        }
      }
    }
  }

  /** Constructs an `xsbti.Position` pointing to `file` at `srcLine`. */
  private def remappedPosition(file: File, srcLine: Int): xsbti.Position =
    new xsbti.Position {
      override def line(): Optional[Integer] =
        Optional.of(srcLine.asInstanceOf[Integer])
      override def lineContent():  String                 = ""
      override def offset():       Optional[Integer]      = Optional.empty()
      override def pointer():      Optional[Integer]      = Optional.empty()
      override def pointerSpace(): Optional[String]       = Optional.empty()
      override def sourcePath():   Optional[String]       = Optional.of(file.getAbsolutePath)
      override def sourceFile():   Optional[java.io.File] = Optional.of(file)
    }
}
