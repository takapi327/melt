/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import java.net.URI
import java.nio.file.Paths

/** Helpers for turning LSP document URIs into filesystem information. */
object UriUtil:

  /** Extracts the file name from a document URI, percent-decoding it so
    * `file:///p/My%20File.melt` yields `My File.melt` rather than the literal
    * `My%20File.melt`.
    *
    * Falls back to naive `file:` prefix stripping for inputs that are not valid
    * `file:` URIs (e.g. a bare path), so it never throws.
    */
  def filename(uri: String): String =
    val path =
      try Paths.get(URI.create(uri))
      catch
        case _: Exception =>
          Paths.get(uri.stripPrefix("file:///").stripPrefix("file://").stripPrefix("file:"))
    Option(path.getFileName).map(_.toString).getOrElse(path.toString)
