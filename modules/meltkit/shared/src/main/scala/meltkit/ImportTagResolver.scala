/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.Escape

/** Resolves `.melt` string literal import paths to HTML `<link>` / `<script>` tags.
  *
  * Shared by all server-side adapter contexts (`Http4sMeltContext`, `SsgMeltContext`,
  * `NodeMeltContext`).  Not part of the public API.
  *
  * Tag selection by extension:
  *   - `.css` / `.scss` / `.less` / `.sass` → `<link rel="stylesheet" href="...">`
  *   - `.js` / `.mjs` / `.ts` / `.jsx` / `.tsx` → `<script type="module" src="..."></script>`
  *   - Anything else → `""` (caller should filter empty strings)
  *
  * Path resolution:
  *   - If the path appears in the manifest, the hashed output file is used:
  *     `basePath + "/" + entry.file` (e.g. `"/assets/global-abc.css"`)
  *   - Otherwise the source path is used as-is (Vite dev-server serves it directly).
  *
  * The `href`/`src` value is run through [[Escape.attr]] before embedding in HTML.
  *
  * CSP nonce:
  *   - When `nonce` is provided, `<script type="module">` tags receive a `nonce="..."` attribute.
  *   - `<link rel="stylesheet">` tags do not need a nonce (controlled by `style-src` origin).
  */
private[meltkit] object ImportTagResolver:

  /** Resolves a single import path to a `<link>` or `<script>` tag string.
    *
    * @param path     the import path as written in `.melt` (e.g. `"/styles/global.css"`)
    * @param manifest the [[ViteManifest]] for production path resolution
    * @param basePath the app's deployment root path (e.g. `""` for root, `"/myapp"` for sub-path)
    * @param nonce    optional CSP nonce; when present, added to `<script type="module">` tags only
    * @return the HTML tag string, or `""` for unknown extensions
    */
  def resolveTag(path: String, manifest: ViteManifest, basePath: String, nonce: Option[String] = None): String =
    val href = manifest.fileForSourcePath(path) match
      case Some(file) => s"${ basePath.stripSuffix("/") }/$file"
      case None       => path
    val safeHref = Escape.attr(href)
    path.split('.').lastOption.map(_.toLowerCase).getOrElse("") match
      case "css" | "scss" | "less" | "sass"       =>
        s"""<link rel="stylesheet" href="$safeHref">"""
      case "js" | "mjs" | "ts" | "jsx" | "tsx"   =>
        val nonceAttr = nonce.fold("")(n => s""" nonce="$n"""")
        s"""<script type="module" src="$safeHref"$nonceAttr></script>"""
      case _                                       => ""

  /** Resolves a list of import paths to deduplicated, non-empty HTML tags joined by newlines.
    *
    * @param paths    the import paths collected in [[melt.runtime.render.RenderResult.imports]]
    * @param manifest the [[ViteManifest]] for production path resolution
    * @param basePath the app's deployment root path
    * @param nonce    optional CSP nonce passed through to [[resolveTag]]
    * @return a newline-joined string of `<link>` / `<script>` tags (may be empty when `paths` is Nil)
    */
  def resolveTags(paths: List[String], manifest: ViteManifest, basePath: String, nonce: Option[String] = None): String =
    paths.map(resolveTag(_, manifest, basePath, nonce)).filter(_.nonEmpty).distinct.mkString("\n")
