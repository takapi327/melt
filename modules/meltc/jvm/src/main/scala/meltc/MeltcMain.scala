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
    "Usage: MeltcMain <input.melt> <output.scala> <ObjectName> <package> " +
      "[--mode spa|ssr] [--hydration]"

  def main(args: Array[String]): Unit =
    if args.length < 4 then
      System.err.println(Usage)
      sys.exit(1)

    val inputPath  = Paths.get(args(0))
    val outputPath = Paths.get(args(1))
    val objectName = args(2)
    val pkg        = args(3)

    val (mode, hydration) = parseExtras(args.drop(4)) match
      case Right(v)  => v
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
      mode,
      hydration
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

  /** Parses optional trailing flags: `--mode spa|ssr` and the boolean
    * `--hydration` switch. Returns the resolved `(CompileMode, hydration)`
    * pair or an error message.
    */
  private def parseExtras(extras: Array[String]): Either[String, (CompileMode, Boolean)] =
    var mode      = CompileMode.SPA
    var hydration = false
    var i         = 0
    val args      = extras
    while i < args.length do
      args(i) match
        case "--mode" =>
          if i + 1 >= args.length then return Left("--mode requires a value ('spa' or 'ssr')")
          args(i + 1).toLowerCase match
            case "spa" => mode = CompileMode.SPA
            case "ssr" => mode = CompileMode.SSR
            case other => return Left(s"unknown --mode value '$other' (expected 'spa' or 'ssr')")
          i += 2
        case "--hydration" =>
          hydration = true
          i += 1
        case other =>
          return Left(s"unrecognised argument: '$other'")
    Right((mode, hydration))
