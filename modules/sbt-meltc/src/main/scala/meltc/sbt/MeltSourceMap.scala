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

  /** Cache keyed by File with the lastModified timestamp stored in the value.
    * Limits the map to one entry per generated `.scala` file while still
    * invalidating stale entries when the file changes across recompilations.
    */
  private val cache =
    new ConcurrentHashMap[File, (Option[MeltGeneratedSource.Meta], Long)]()

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
        val lastMod = genFile.lastModified()
        val meta    = {
          val cached = cache.get(genFile)
          if (cached != null && cached._2 == lastMod) cached._1
          else {
            val result = MeltGeneratedSource.read(genFile)
            cache.put(genFile, (result, lastMod))
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
              MeltGeneratedSource.mapPosition(meta, genLine) match {
                case None                    => None
                case Some((srcLine, srcCol)) =>
                  Some(remappedPosition(meltFile, srcLine, srcCol))
              }
            }
        }
      }
    }
  }

  /** Constructs an `xsbti.Position` pointing to `file` at `srcLine` and `srcCol`. */
  private def remappedPosition(file: File, srcLine: Int, srcCol: Int): xsbti.Position =
    new xsbti.Position {
      override def line(): Optional[Integer] =
        Optional.of(srcLine.asInstanceOf[Integer])
      override def lineContent(): String            = ""
      override def offset():      Optional[Integer] = Optional.empty()
      // pointer() is 0-based within the line; srcCol is 1-based
      override def pointer():      Optional[Integer]      = Optional.of((srcCol - 1).asInstanceOf[Integer])
      override def pointerSpace(): Optional[String]       = Optional.empty()
      override def sourcePath():   Optional[String]       = Optional.of(file.getAbsolutePath)
      override def sourceFile():   Optional[java.io.File] = Optional.of(file)
    }
}
