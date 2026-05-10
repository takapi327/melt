/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.render.RenderResult
import melt.runtime.Escape

/** An HTML shell template for SPA and SSR applications.
  *
  * Holds the raw content of an `index.html` that contains `%melt.X%`
  * placeholders. [[render]] replaces those placeholders with the appropriate
  * content depending on the rendering mode.
  *
  * Load the template once at application startup and reuse it across
  * requests. Instances are immutable and thread-safe.
  *
  * == Placeholders ==
  *
  *   - `%melt.head%`  — replaced with script tags (SPA) or `RenderResult.head`
  *                      plus collected stylesheet / preload link tags (SSR)
  *   - `%melt.body%`  — replaced with `RenderResult.body` plus hydration
  *                      bootstrap scripts (SSR only)
  *   - `%melt.title%` — replaced with the HTML-escaped `title` argument (SSR only)
  *   - `%melt.lang%`  — replaced with the attribute-escaped `lang` argument (SSR only)
  *   - `%melt.nonce%` — replaced with the CSP nonce value (SSR only, no escaping applied);
  *                      replaced with an empty string when no nonce is provided
  *   - `%melt.X%`     — replaced with the HTML-escaped value of key `X`
  *                      in the `vars` map (SSR only)
  *
  * == SPA example ==
  *
  * `src/main/resources/index.html`:
  * {{{
  * <!doctype html>
  * <html>
  *   <head>
  *     <meta charset="UTF-8">
  *     %melt.head%
  *   </head>
  *   <body></body>
  * </html>
  * }}}
  *
  * Server setup:
  * {{{
  * val httpApp = Http4sAdapter.spaRoutes(app, AssetManifest.clientDistDir, AssetManifest.manifest)
  *   .map(_.orNotFound)
  * }}}
  *
  * == SSR example ==
  *
  * `src/main/resources/index.html`:
  * {{{
  * <!doctype html>
  * <html lang="%melt.lang%">
  *   <head>
  *     <meta charset="UTF-8">
  *     <title>%melt.title%</title>
  *     %melt.head%
  *   </head>
  *   <body>
  *     %melt.body%
  *   </body>
  * </html>
  * }}}
  *
  * Server setup:
  * {{{
  * val httpApp =
  *   Http4sAdapter(app, AssetManifest.clientDistDir, AssetManifest.manifest)
  *     .map(_.routes.orNotFound)
  * }}}
  */
final class Template private[meltkit] (private val raw: String):

  // ── SPA ────────────────────────────────────────────────────────────────────

  /** Replaces `%melt.head%` with `headContent` and returns the resulting HTML.
    *
    * Used for SPA shells where the body is static and only script tags need
    * to be injected.
    */
  def render(headContent: String): String =
    raw.replace("%melt.head%", headContent)

  // ── SSR without hydration ─────────────────────────────────────────────────

  /** Renders the template with SSR output, without hydration asset injection.
    *
    * @param result the component render result
    * @param title  page title; if non-empty, HTML-escaped before substitution.
    *               If empty, `result.title` is used directly (already escaped).
    * @param lang   value for `%melt.lang%`, default `"en"`
    * @param vars   extra placeholder values; each `k -> v` replaces
    *               `%melt.k%` with `Escape.html(v)`. Reserved names
    *               (`head`, `body`, `title`, `lang`) are ignored.
    */
  def render(
    result: RenderResult,
    title:  String = "",
    lang:   String = "en",
    vars:   Map[String, String] = Map.empty
  ): String =
    val effectiveTitle =
      if title.nonEmpty then Escape.html(title)
      else result.title.getOrElse("")
    renderInternal(result, effectiveTitle, lang, vars, extraHead = "", extraBody = "", nonce = None)

  // ── SSR with hydration ────────────────────────────────────────────────────

  /** Renders with hydration asset injection (default options).
    *
    * Resolves JS / CSS chunks via the manifest and injects:
    *   - `<link rel="stylesheet" href="...">` into `%melt.head%`
    *   - `<link rel="modulepreload" href="...">` into `%melt.head%`
    *   - `<script type="module">import(...).then(m => m.hydrate())</script>`
    *     into `%melt.body%`
    *   - `<script type="application/json" data-melt-props="...">` blobs
    *     into `%melt.body%`
    */
  def render(result: RenderResult, manifest: ViteManifest): String =
    render(result, manifest, title = "", lang = "en", basePath = "", vars = Map.empty, nonce = None)

  /** Renders with hydration asset injection and an explicit title. */
  def render(result: RenderResult, manifest: ViteManifest, title: String): String =
    render(result, manifest, title, lang = "en", basePath = "", vars = Map.empty, nonce = None)

  /** Renders with hydration asset injection and full control over all options. */
  def render(
    result:   RenderResult,
    manifest: ViteManifest,
    title:    String,
    lang:     String,
    basePath: String,
    vars:     Map[String, String]
  ): String =
    render(result, manifest, title, lang, basePath, vars, nonce = None)

  /** Renders with hydration asset injection, full control over all options, and CSP nonce support.
    *
    * When `nonce` is provided:
    *   - Each `<script type="module">` bootstrap tag receives a `nonce="..."` attribute.
    *   - `%melt.nonce%` placeholders in the template are replaced with the nonce value
    *     (no HTML escaping; URL-safe Base64 characters are safe in HTML attributes).
    *
    * @param nonce CSP nonce to inject into inline scripts; `None` produces the same
    *              output as the 6-argument overload
    */
  def render(
    result:   RenderResult,
    manifest: ViteManifest,
    title:    String,
    lang:     String,
    basePath: String,
    vars:     Map[String, String],
    nonce:    Option[String]
  ): String =
    val effectiveTitle =
      if title.nonEmpty then Escape.html(title)
      else result.title.getOrElse("")

    val jsChunks  = result.components.flatMap(manifest.chunksFor).toList.distinct
    val cssChunks = result.components.flatMap(manifest.cssFor).toList.distinct

    val strippedBase = basePath.stripSuffix("/")

    val stylesheets = cssChunks
      .map(f => s"""<link rel="stylesheet" href="$strippedBase/$f">""")
      .mkString("\n")

    val preloads = jsChunks
      .map(f => s"""<link rel="modulepreload" href="$strippedBase/$f">""")
      .mkString("\n")

    val nonceAttr = nonce.fold("")(n => s""" nonce="$n"""")
    val bootstrap = result.components.toList.distinct
      .flatMap { moduleId =>
        manifest.chunksFor(moduleId).lastOption.map { entryChunk =>
          s"""<script type="module"$nonceAttr>import("$strippedBase/$entryChunk").then(m => m.hydrate?.())</script>"""
        }
      }
      .mkString("\n")

    val propsBlobs = result.components.toList.distinct
      .flatMap { moduleId =>
        result.hydrationProps.get(moduleId).map { json =>
          val attr = Escape.attr(moduleId)
          s"""<script type="application/json" data-melt-props="$attr">$json</script>"""
        }
      }
      .mkString("\n")

    val extraHead = List(stylesheets, preloads).filter(_.nonEmpty).mkString("\n")
    val extraBody = List(propsBlobs, bootstrap).filter(_.nonEmpty).mkString("\n")

    renderInternal(result, effectiveTitle, lang, vars, extraHead, extraBody, nonce)

  private def renderInternal(
    result:         RenderResult,
    effectiveTitle: String,
    lang:           String,
    vars:           Map[String, String],
    extraHead:      String,
    extraBody:      String,
    nonce:          Option[String] = None
  ): String =
    // Inject component-scoped CSS from result.css once here so that
    // sub-component CSS merged via ServerRenderer.merge is never duplicated.
    val cssHtml = result.css.toList
      .sortBy(_.scopeId)
      .map(e => s"""<style id="${ e.scopeId }">${ e.code }</style>""")
      .mkString("\n")

    val parts       = List(result.head, cssHtml, extraHead).filter(_.nonEmpty)
    val headContent = parts.mkString("\n")

    val bodyContent =
      if extraBody.isEmpty then result.body
      else s"${ result.body }\n$extraBody"

    var out = raw
    out = out.replace("%melt.lang%", Escape.attr(lang))
    out = out.replace("%melt.title%", effectiveTitle)
    out = out.replace("%melt.head%", headContent)
    out = out.replace("%melt.body%", bodyContent)
    // Nonce is replaced without HTML escaping: URL-safe Base64 chars are safe in HTML attributes.
    // Replaced with empty string when no nonce is configured (e.g. SPA mode or CSP disabled).
    out = out.replace("%melt.nonce%", nonce.getOrElse(""))
    vars.foreach {
      case (k, v) =>
        // "nonce" is reserved; user-supplied vars must not override the nonce placeholder.
        if k != "head" && k != "body" && k != "title" && k != "lang" && k != "nonce" then
          out = out.replace(s"%melt.$k%", Escape.html(v))
    }
    out

object Template:

  /** Builds a [[Template]] from an in-memory string. Primarily for tests. */
  def fromString(content: String): Template = new Template(content)
