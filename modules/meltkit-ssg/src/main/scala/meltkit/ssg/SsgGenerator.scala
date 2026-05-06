/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.{ Files, Path }

import meltkit.{ PathSegment, PlainResponse, Response, SyncRunner }

/** Core static site generation engine.
  *
  * Iterates over the paths returned by [[SsgApp.paths]], matches each one
  * against the registered routes, runs the handler synchronously via
  * [[SyncRunner]], and writes the resulting HTML to disk.
  */
object SsgGenerator:

  /** Runs static site generation for `app` according to `config`.
    *
    * @tparam F effect type; must have [[Pure]] and [[SyncRunner]] instances
    */
  def run[F[_]: SyncRunner](app: SsgApp[F], config: SsgConfig): Unit =
    val out = config.outputDir

    // 1. Clean output directory
    if config.cleanOutput && Files.exists(out) then deleteDirectory(out)
    Files.createDirectories(out)

    // 2. Obtain all paths (may involve async DB/CMS queries)
    val allPaths: List[String] = SyncRunner[F].runSync(app.paths)

    // 3. Generate HTML for each path
    allPaths.foreach { rawPath =>
      // Route matching uses the original path segments.
      // normalizePath converts "/about" → "/about/index.html", which would
      // break PathSegment.matches (the "about" segment would not match "index.html").
      val segments       = splitPath(rawPath)
      val normalizedPath = normalizePath(rawPath)

      val maybeHandler = app.kit.routes
        .filter(_.method == "GET")
        .flatMap { route =>
          // PathSegment.matches is private[meltkit]; accessible from meltkit.ssg
          if PathSegment.matches(route.segments, segments) then
            val rawValues =
              route.segments
                .zip(segments)
                .collect { case (PathSegment.Param(_), v) => v }
            val factory = new SsgMeltContextFactory[F](
              requestPath  = rawPath,
              template     = app.template,
              manifest     = app.manifest,
              basePath     = app.basePath,
              useHydration = app.useHydration,
              defaultTitle = app.defaultTitle,
              defaultLang  = app.defaultLang
            )
            route.tryHandle(rawValues, factory).map(_ -> factory)
          else None
        }
        .headOption

      maybeHandler match
        case None =>
          System.err.println(s"[meltkit-ssg] Warning: no route matched '$rawPath' — skipped")

        case Some((handler, factory)) =>
          val response: Response =
            try SyncRunner[F].runSync(handler())
            catch
              case e: Throwable =>
                throw new RuntimeException(
                  s"[meltkit-ssg] Failed to render '$rawPath': ${ e.getMessage }",
                  e
                )

          response match
            case plain: PlainResponse if plain.body.nonEmpty =>
              val outFile = out.resolve(normalizedPath.stripPrefix("/"))
              Files.createDirectories(outFile.getParent)
              Files.writeString(outFile, plain.body)
              if !config.quiet then println(s"[meltkit-ssg] Generated: $normalizedPath")

            case _ =>
              if !config.quiet then
                println(s"[meltkit-ssg] Skipped (non-HTML response): $rawPath")
    }

    // 4. Copy Vite assets
    config.assetsDir.foreach { assetsDir =>
      if Files.isDirectory(assetsDir) then
        val target = out.resolve("assets")
        copyDirectory(assetsDir, target)
        if !config.quiet then
          println(s"[meltkit-ssg] Copied assets: $assetsDir -> $target")
    }

  /** Normalises a URL path to a file path.
    *
    * `"/"` → `"/index.html"`, `"/about"` → `"/about/index.html"`,
    * `"/feed.xml"` → `"/feed.xml"` (already has an extension).
    */
  private def normalizePath(path: String): String =
    val p = if path.isEmpty then "/" else path
    if p.endsWith("/") then p + "index.html"
    else if !p.contains('.') then p + "/index.html"
    else p

  /** Splits a URL path into segments for route matching.
    *
    * `"/about"` → `List("about")`, `"/"` → `Nil`,
    * `"/api/v1/users"` → `List("api", "v1", "users")`.
    */
  private def splitPath(path: String): List[String] =
    path.stripPrefix("/").split('/').toList.filter(_.nonEmpty)

  /** Deletes `dir` and all its contents, ensuring the walk stream is closed. */
  private def deleteDirectory(dir: Path): Unit =
    val stream = Files.walk(dir)
    try stream.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
    finally stream.close()

  /** Copies `src` into `dst`, creating parent directories as needed. */
  private def copyDirectory(src: Path, dst: Path): Unit =
    val stream = Files.walk(src)
    try
      stream.forEach { srcFile =>
        val rel     = src.relativize(srcFile)
        val dstFile = dst.resolve(rel)
        if Files.isDirectory(srcFile) then Files.createDirectories(dstFile)
        else
          Files.createDirectories(dstFile.getParent)
          Files.copy(srcFile, dstFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
    finally stream.close()
