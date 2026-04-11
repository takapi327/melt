/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import melt.runtime.Escape

/** Assembles a complete HTML document from a [[RenderResult]].
  *
  * The structure mirrors SvelteKit's default `app.html`:
  * `<meta charset="UTF-8">` and
  * `<meta name="viewport" content="width=device-width, initial-scale=1">`
  * are always emitted, followed by the optional `<title>`, the
  * component-supplied head content (from `<melt:head>`), then any
  * user-provided `extraHead`.
  *
  * == Character encoding ==
  *
  * `<meta charset="UTF-8">` is always emitted as the first child of
  * `<head>`. HTML5 preparsers only honor a charset declaration that appears
  * within the first 1024 bytes, so any later `<meta charset>` from
  * `result.head` or `extraHead` is silently ignored by the browser.
  *
  * Following Svelte 5 / SvelteKit, melt does not detect or warn on
  * duplicate charset declarations — correctness is delegated to the
  * browser's "first wins" semantics. The charset is not configurable:
  * all melt output is UTF-8. If you need a different encoding, encode the
  * resulting `String` yourself before writing it to the response.
  *
  * == Related: HTTP Content-Type ==
  *
  * For best results, also set `Content-Type: text/html; charset=utf-8` on
  * the HTTP response so the browser can commit to UTF-8 before parsing any
  * HTML.
  */
object Layout:

  /** SSR-only document assembler (no hydration JS injected). */
  def document(
    result:    RenderResult,
    title:     String = "",
    lang:      String = "en",
    extraHead: String = ""
  ): String =
    val titleTag =
      if title.nonEmpty then s"<title>${ Escape.html(title) }</title>"
      else ""

    s"""<!DOCTYPE html>
       |<html lang="$lang">
       |<head>
       |<meta charset="UTF-8">
       |<meta name="viewport" content="width=device-width, initial-scale=1">
       |$titleTag
       |${ result.head }
       |$extraHead
       |</head>
       |<body>
       |${ result.body }
       |</body>
       |</html>""".stripMargin
