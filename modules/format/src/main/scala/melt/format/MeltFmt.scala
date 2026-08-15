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
  * A `.melt` file is configured entirely by `.meltfmt.conf`: it carries the
  * Melt-specific layout (`melt.*`) and — for the `<script>` Scala — the scalafmt
  * style, which is typically brought in with `include ".scalafmt.conf"` to reuse
  * the project's existing scalafmt settings. Formats the `<script>` sections with
  * scalafmt, the `<style>` CSS with [[CssFormatter]] (SCSS passed through), and
  * the HTML template with [[TemplateFmt]] (AST-equality valve). Everything outside
  * the recognized sections is byte-preserved. See `memo/design-melt-fmt.md`,
  * `memo/design-melt-css-fmt.md`, `memo/design-melt-template-fmt.md`.
  *
  * When no scalafmt config is available, `<script>` sections are left unchanged
  * (with a warning) rather than failing — CSS/template still format.
  */
object MeltFmt:

  /** Formats one `.melt` source with a scalafmt config path (script formatting on). */
  def format(source: String, scalafmtConfig: Path): Either[String, String] =
    format(source, Some(new ScriptFormatter(scalafmtConfig)))

  /** Formats using an optional [[ScriptFormatter]] plus CSS/template options.
    * `scriptFormatter = None` means no scalafmt config is available: `<script>`
    * sections are passed through (with a warning) while CSS/template still format.
    */
  def format(
    source:          String,
    scriptFormatter: Option[ScriptFormatter],
    cssOptions:      CssFormatter.Options                    = CssFormatter.Options(),
    templateOptions: melt.template.TemplateFormatter.Options = melt.template.TemplateFormatter.Options()
  ): Either[String, String] =
    MeltFormatter.formatE(
      source,
      { (region, inner) =>
        region.kind match
          case RegionKind.Style    => StyleFormatter.format(inner, region.styleLang, cssOptions)
          case RegionKind.Template => TemplateFmt.format(inner, templateOptions)
          case _                   =>
            scriptFormatter match
              case Some(f) => f.format(inner)
              case None    =>
                if inner.strip.isEmpty then Right(inner)
                else
                  System.err.println(
                    "[meltfmt] <script> left unchanged: no scalafmt config found " +
                      "(add `include \".scalafmt.conf\"` to .meltfmt.conf, or provide .scalafmt.conf)"
                  )
                  Right(inner)
      }
    )
