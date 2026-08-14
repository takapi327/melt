/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.Path

import melt.css.CssFormatter
import melt.parser.MeltRegionScanner.RegionKind

/** Top-level `.melt` formatter.
  *
  * Formats the Scala `<script>` / `<script module>` sections with scalafmt and
  * the `<style>` CSS with [[CssFormatter]] (SCSS is passed through). The
  * template is left untouched. Everything outside the formatted sections is
  * preserved byte-for-byte. See `memo/design-melt-fmt.md` /
  * `memo/design-melt-css-fmt.md`.
  */
object MeltFmt:

  /** Formats one `.melt` source. `scalafmtConfig` is a path to `.scalafmt.conf`. */
  def format(source: String, scalafmtConfig: Path): Either[String, String] =
    format(source, new ScriptFormatter(scalafmtConfig))

  /** Formats using an existing [[ScriptFormatter]] and CSS options (reuse across
    * many files). `cssOptions` drives the `<style>` formatter (`.meltfmt.conf`).
    */
  def format(
    source:          String,
    scriptFormatter: ScriptFormatter,
    cssOptions:      CssFormatter.Options = CssFormatter.Options()
  ): Either[String, String] =
    MeltFormatter.formatE(
      source,
      { (region, inner) =>
        region.kind match
          case RegionKind.Style => StyleFormatter.format(inner, region.styleLang, cssOptions)
          case _                => scriptFormatter.format(inner)
      }
    )
