/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.{ Files, Path, Paths }

import scala.jdk.CollectionConverters.*

/** CLI entry point for the `.melt` formatter (Phase 1: `<script>` sections only).
  *
  * {{{
  * MeltFmtMain [--check] [--config <path>] <file-or-dir>...
  * }}}
  *   - `--check`         verify only; throw (non-zero task exit) if any file would change
  *   - `--config <path>` path to `.scalafmt.conf` (default: `./.scalafmt.conf`)
  *
  * Directories are scanned recursively for `*.melt`. Runs in-process, so it
  * throws (rather than `System.exit`) on failure to avoid killing a host sbt JVM.
  */
object MeltFmtMain:

  def main(args: Array[String]): Unit =
    val (check, configOpt, targets) = parse(args.toList)
    val conf = configOpt.getOrElse(Paths.get(".scalafmt.conf"))
    if !Files.isRegularFile(conf) then
      throw new RuntimeException(s"[meltfmt] .scalafmt.conf not found: ${conf.toAbsolutePath}")

    val files = targets.flatMap(meltFiles).distinct
    if files.isEmpty then
      println("[meltfmt] no .melt files found")
      return

    val formatter   = new ScriptFormatter(conf)
    val unformatted = scala.collection.mutable.ListBuffer.empty[String] // fixable by meltFmt
    val skipped     = scala.collection.mutable.ListBuffer.empty[String] // unparseable — left as-is
    var changed     = 0

    files.foreach { f =>
      val src = Files.readString(f)
      MeltFmt.format(src, formatter) match
        case Right(out) if out == src => ()
        case Right(_) if check        => unformatted += f.toString
        case Right(out)               =>
          Files.writeString(f, out); changed += 1; println(s"[meltfmt] formatted $f")
        case Left(err) => skipped += s"$f: $err" // never corrupt an unparseable file
    }

    // Unparseable files are warned about but never fail the run: meltFmt cannot
    // fix them and must not touch them (e.g. a `</script>` inside a script string).
    if skipped.nonEmpty then
      System.err.println(s"[meltfmt] skipped ${skipped.size} unparseable file(s):")
      skipped.foreach(s => System.err.println("  " + s))

    if check then
      if unformatted.nonEmpty then
        throw new RuntimeException(
          s"[meltfmt] ${unformatted.size} of ${files.size} file(s) need formatting (run `sbt meltFmt`):\n" +
            unformatted.map("  " + _).mkString("\n")
        )
      else println(s"[meltfmt] check OK (${files.size} file(s), ${skipped.size} skipped)")
    else
      println(s"[meltfmt] formatted $changed of ${files.size} file(s), ${skipped.size} skipped")

  private def parse(args: List[String]): (Boolean, Option[Path], List[Path]) =
    def loop(as: List[String], check: Boolean, cfg: Option[Path], acc: List[Path]): (Boolean, Option[Path], List[Path]) =
      as match
        case Nil                     => (check, cfg, acc.reverse)
        case "--check" :: rest       => loop(rest, true, cfg, acc)
        case "--config" :: p :: rest => loop(rest, check, Some(Paths.get(p)), acc)
        case other :: rest           => loop(rest, check, cfg, Paths.get(other) :: acc)
    loop(args, false, None, Nil)

  private def meltFiles(target: Path): List[Path] =
    if Files.isRegularFile(target) then
      if target.toString.endsWith(".melt") then List(target) else Nil
    else if Files.isDirectory(target) then
      Files
        .walk(target)
        .iterator
        .asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".melt"))
        .toList
    else Nil
