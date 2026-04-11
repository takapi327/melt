/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths }

/** CLI entry point for the meltc compiler (JVM platform only).
  *
  * Usage:
  * {{{
  *   java -cp <classpath> meltc.MeltcMain \
  *        <input.melt> <output.scala> <ObjectName> <package> [--mode spa|ssr]
  * }}}
  *
  * `--mode` defaults to `spa` for backwards compatibility with existing
  * sbt-meltc builds. Pass `--mode ssr` to produce JVM HTML-string-rendering
  * code from the same `.melt` source.
  *
  * Exits with code 0 on success, 1 on error. Error messages are written
  * to stderr.
  */
object MeltcMain:

  private val Usage =
    "Usage: MeltcMain <input.melt> <output.scala> <ObjectName> <package> [--mode spa|ssr]"

  def main(args: Array[String]): Unit =
    if args.length < 4 then
      System.err.println(Usage)
      sys.exit(1)

    val inputPath  = Paths.get(args(0))
    val outputPath = Paths.get(args(1))
    val objectName = args(2)
    val pkg        = args(3)

    val mode = parseMode(args.drop(4)) match
      case Right(m)  => m
      case Left(err) =>
        System.err.println(s"meltc: $err")
        System.err.println(Usage)
        sys.exit(1)

    val source =
      try new String(Files.readAllBytes(inputPath), StandardCharsets.UTF_8)
      catch
        case e: Exception =>
          System.err.println(s"meltc: cannot read ${ inputPath }: ${ e.getMessage }")
          sys.exit(1)

    val result = MeltCompiler.compile(
      source,
      inputPath.getFileName.toString,
      objectName,
      pkg,
      mode
    )

    result.warnings.foreach(w => System.err.println(s"meltc warning: ${ w.message }"))

    if result.errors.nonEmpty then
      result.errors.foreach(e => System.err.println(s"meltc error: ${ e.message }"))
      sys.exit(1)

    result.scalaCode match
      case None =>
        System.err.println("meltc: code generation produced no output")
        sys.exit(1)
      case Some(code) =>
        try
          Option(outputPath.getParent).foreach(Files.createDirectories(_))
          Files.write(outputPath, code.getBytes(StandardCharsets.UTF_8))
        catch
          case e: Exception =>
            System.err.println(s"meltc: cannot write ${ outputPath }: ${ e.getMessage }")
            sys.exit(1)

  /** Parses optional trailing flags. Phase A only supports `--mode`. */
  private def parseMode(extras: Array[String]): Either[String, CompileMode] =
    val args = extras.toList
    args match
      case Nil => Right(CompileMode.SPA)
      case "--mode" :: value :: Nil =>
        value.toLowerCase match
          case "spa" => Right(CompileMode.SPA)
          case "ssr" => Right(CompileMode.SSR)
          case other => Left(s"unknown --mode value '$other' (expected 'spa' or 'ssr')")
      case "--mode" :: Nil =>
        Left("--mode requires a value ('spa' or 'ssr')")
      case other =>
        Left(s"unrecognised arguments: ${ other.mkString(" ") }")
