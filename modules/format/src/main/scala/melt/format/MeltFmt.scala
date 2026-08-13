/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.Path

import melt.parser.MeltRegionScanner.RegionKind

/** Top-level `.melt` formatter (Phase 1).
  *
  * Formats the Scala `<script>` / `<script module>` sections with scalafmt; the
  * `<style>` section and the template are left untouched (Phase 1 passthrough —
  * see `memo/design-melt-fmt.md`). Everything outside the formatted sections is
  * preserved byte-for-byte.
  */
object MeltFmt:

  /** Formats one `.melt` source. `scalafmtConfig` is a path to `.scalafmt.conf`. */
  def format(source: String, scalafmtConfig: Path): Either[String, String] =
    format(source, new ScriptFormatter(scalafmtConfig))

  /** Formats using an existing [[ScriptFormatter]] (reuse across many files). */
  def format(source: String, scriptFormatter: ScriptFormatter): Either[String, String] =
    MeltFormatter.formatE(
      source,
      {
        case (RegionKind.Style, inner) => Right(inner) // Phase 1: passthrough
        case (_, inner)                => scriptFormatter.format(inner)
      }
    )
