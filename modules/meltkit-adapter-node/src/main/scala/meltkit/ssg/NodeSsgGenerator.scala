/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.concurrent.{ ExecutionContext, Future }
import scala.scalajs.js
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.{
  MeltContext,
  MeltContextFactory,
  NodeMeltContext,
  NodePath,
  PathSegment,
  PlainResponse,
  PrerenderOption,
  Response,
  ServerConfig,
  ServerMeltKitPlatform,
  SyncRunner
}
import meltkit.codec.BodyDecoder

/** Core static site generation engine for Node.js.
  *
  * Mirrors the JVM [[meltkit.ssg.SsgGenerator]] but uses Node.js `fs` sync APIs
  * (via [[NodeFsSsg]]) instead of `java.nio.file`.
  *
  * Collects routes registered with a [[meltkit.PageOptions]] argument where
  * [[meltkit.PageOptions.prerender]] is set to [[PrerenderOption.On]] or
  * [[PrerenderOption.Auto]], derives concrete URL paths for each, runs the handler
  * synchronously via [[SyncRunner]], and writes the resulting HTML to disk.
  *
  * Static routes (no path parameters) yield a single output path derived from the route
  * segments. Dynamic routes (containing [[PathSegment.Param]]) require concrete paths to
  * be enumerated via [[meltkit.PageOptions.entries]].
  */
object NodeSsgGenerator:

  /** Runs static site generation for `app` according to `config`.
    *
    * `config.outputDir` must be set; an [[IllegalArgumentException]] is thrown if it is `None`.
    */
  def run(app: ServerMeltKitPlatform[Future], config: ServerConfig)(using ExecutionContext): Unit =
    val out = config.outputDir.getOrElse(
      throw new IllegalArgumentException(
        "[meltkit-ssg] ServerConfig.outputDir must be set to run NodeSsgGenerator"
      )
    )

    // 1. Clean output directory
    if config.cleanOutput && NodeFsSsg.existsSync(out) then
      NodeFsSsg.rmSync(out, js.Dynamic.literal(recursive = true, force = true))
    NodeFsSsg.mkdirSync(out, js.Dynamic.literal(recursive = true))

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
        val factory   = new MeltContextFactory[Future, RenderResult]:
          def build[P <: AnyNamedTuple, B](
            params:      P,
            bodyDecoder: BodyDecoder[B]
          ): MeltContext[Future, P, B, RenderResult] =
            NodeMeltContext(
              params       = params,
              requestPath  = rawPath,
              bodyDecoder  = bodyDecoder,
              rawBody      = Future.successful(""),
              templateOpt  = Some(config.template),
              manifest     = config.manifest,
              lang         = config.defaultLang,
              basePath     = config.basePath,
              defaultTitle = config.defaultTitle
            )

        route.tryHandle(rawValues, factory) match
          case None =>
            System.err.println(s"[meltkit-ssg] Warning: route did not match '$rawPath' — skipped")

          case Some(handler) =>
            val response: Response =
              try SyncRunner[Future].runSync(handler())
              catch
                case e: Throwable =>
                  throw new RuntimeException(
                    s"[meltkit-ssg] Failed to render '$rawPath': ${ e.getMessage }",
                    e
                  )

            response match
              case plain: PlainResponse if plain.body.nonEmpty && plain.contentType.startsWith("text/") =>
                val normalizedPath = normalizePath(rawPath)
                val outFile        = NodePath.join(out, normalizedPath.stripPrefix("/"))
                NodeFsSsg.mkdirSync(NodePath.dirname(outFile), js.Dynamic.literal(recursive = true))
                NodeFsSsg.writeFileSync(outFile, plain.body, "utf8")
                if !config.quiet then println(s"[meltkit-ssg] Generated: $normalizedPath")

              case _ =>
                if !config.quiet then println(s"[meltkit-ssg] Skipped (non-text response): $rawPath")
    }

    // 4. Copy Vite assets
    config.assetsDir.foreach { assetsDir =>
      if NodeFsSsg.existsSync(assetsDir) && NodeFsSsg.lstatSync(assetsDir).isDirectory() then
        val target = NodePath.join(out, "assets")
        copyDirectory(assetsDir, target)
        if !config.quiet then println(s"[meltkit-ssg] Copied assets: $assetsDir -> $target")
    }

    // 5. Copy public directory verbatim to output root
    config.publicDir.foreach { publicDir =>
      if NodeFsSsg.existsSync(publicDir) && NodeFsSsg.lstatSync(publicDir).isDirectory() then
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

  /** Recursively copies `src` directory into `dst`.
    *
    * Node.js `readdirSync` returns only direct children, so recursion is handled manually.
    */
  private def copyDirectory(src: String, dst: String): Unit =
    NodeFsSsg.mkdirSync(dst, js.Dynamic.literal(recursive = true))
    NodeFsSsg.readdirSync(src).foreach { entry =>
      val srcPath = NodePath.join(src, entry)
      val dstPath = NodePath.join(dst, entry)
      if NodeFsSsg.lstatSync(srcPath).isDirectory() then copyDirectory(srcPath, dstPath)
      else NodeFsSsg.copyFileSync(srcPath, dstPath)
    }
