/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.{ Files, Path }

import meltkit.{ MeltApp, PathSegment, PlainResponse, PrerenderOption, Response, SyncRunner }

/** Core static site generation engine.
  *
  * Collects routes from [[MeltApp]] that have [[meltkit.PageOptions.prerender]] set to
  * [[PrerenderOption.On]] or [[PrerenderOption.Auto]], derives concrete URL paths for
  * each, runs the handler synchronously via [[SyncRunner]], and writes the resulting
  * HTML to disk.
  *
  * Static routes (no path parameters) yield a single output path derived from the route
  * segments. Dynamic routes (containing [[PathSegment.Param]]) require concrete paths to
  * be enumerated via [[meltkit.PageOptions.entries]].
  */
object SsgGenerator:

  /** Runs static site generation for `app` according to `config`.
    *
    * @tparam F effect type; must have a [[SyncRunner]] instance
    */
  def run[F[_]: SyncRunner](app: MeltApp[F], config: SsgConfig): Unit =
    val out = config.outputDir

    // 1. Clean output directory
    if config.cleanOutput && Files.exists(out) then deleteDirectory(out)
    Files.createDirectories(out)

    // 2. Collect (route, concrete path) pairs for all prerender-enabled routes
    val targets = app.routes
      .filter(r => r.method == "GET" && r.segments != List(PathSegment.Wildcard))
      .flatMap { route =>
        app.pageOptionsFor(route.segments) match
          case Some(opts) if opts.prerender != PrerenderOption.Off =>
            val hasDynamic = route.segments.exists(_.isInstanceOf[PathSegment.Param])
            if !hasDynamic then
              // Static route: derive single path from segments
              val path =
                if route.segments.isEmpty then "/"
                else "/" + route.segments.collect { case PathSegment.Static(v) => v }.mkString("/")
              List(route -> path)
            else if opts.entries.nonEmpty then
              // Dynamic route: use explicitly enumerated paths
              opts.entries.map(route -> _)
            else
              System.err.println(
                s"[meltkit-ssg] Warning: dynamic route has prerender=On but no entries — skipped"
              )
              Nil
          case _ => Nil
      }

    // 3. Generate HTML for each (route, path) pair
    targets.foreach {
      case (route, rawPath) =>
        val segments  = splitPath(rawPath)
        val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
        val factory   = new SsgMeltContextFactory[F](rawPath, config)

        route.tryHandle(rawValues, factory) match
          case None =>
            System.err.println(s"[meltkit-ssg] Warning: route did not match '$rawPath' — skipped")

          case Some(handler) =>
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
                val normalizedPath = normalizePath(rawPath)
                val outFile        = out.resolve(normalizedPath.stripPrefix("/"))
                Files.createDirectories(outFile.getParent)
                Files.writeString(outFile, plain.body)
                if !config.quiet then println(s"[meltkit-ssg] Generated: $normalizedPath")

              case _ =>
                if !config.quiet then println(s"[meltkit-ssg] Skipped (non-HTML response): $rawPath")
    }

    // 4. Copy Vite assets
    config.assetsDir.foreach { assetsDir =>
      if Files.isDirectory(assetsDir) then
        val target = out.resolve("assets")
        copyDirectory(assetsDir, target)
        if !config.quiet then println(s"[meltkit-ssg] Copied assets: $assetsDir -> $target")
    }

    // 5. Copy public directory verbatim to output root
    config.publicDir.foreach { publicDir =>
      if Files.isDirectory(publicDir) then
        copyDirectory(publicDir, out)
        if !config.quiet then println(s"[meltkit-ssg] Copied public: $publicDir -> $out")
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

  /** Splits a URL path into segments for route matching. */
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
