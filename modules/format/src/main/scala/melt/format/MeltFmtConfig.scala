/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.{ Files, Path }

import scala.util.Try

import com.typesafe.config.ConfigFactory

/** Selector-list layout in the CSS formatter. */
enum SelectorListStyle:
  case Newline    // each selector on its own line (default)
  case SingleLine // `a, b, c` on one line

/** Options for the CSS (`<style>`) formatter. */
final case class CssFormatOptions(
  indent:       Int               = 2,
  selectorList: SelectorListStyle = SelectorListStyle.Newline
)

/** Options for the `<script>` formatter.
  *
  * `indent` is only the section's left margin (how far the script body is
  * inset from column 0). The scalafmt *style* itself (maxColumn, align, …) is
  * always taken from `.scalafmt.conf`, never from here.
  */
final case class ScriptFormatOptions(
  indent: Int = 2
)

/** How an element's leaf/text content is laid out in the template formatter. */
enum TemplateContentStyle:
  case Inline   // `<p>hello</p>` (default)
  case Expanded // `<p>\n  hello\n</p>`

/** Options for the HTML template formatter. */
final case class TemplateFormatOptions(
  indent:  Int                  = 2,
  content: TemplateContentStyle = TemplateContentStyle.Inline
)

/** The `.meltfmt.conf` model.
  *
  * Only the `melt.*` namespace is read here; scalafmt style keys (whether inline
  * or pulled in via `include ".scalafmt.conf"`) are ignored — the script
  * formatter reads `.scalafmt.conf` directly for the Scala *style*, and takes
  * only its left-margin `indent` from here. Missing keys fall back to defaults.
  */
final case class MeltFmtConfig(
  css:      CssFormatOptions      = CssFormatOptions(),
  script:   ScriptFormatOptions   = ScriptFormatOptions(),
  template: TemplateFormatOptions = TemplateFormatOptions()
)

object MeltFmtConfig:

  val FileName = ".meltfmt.conf"

  /** All-defaults config (used when no `.meltfmt.conf` is found). */
  val default: MeltFmtConfig = MeltFmtConfig()

  /** Finds the nearest `.meltfmt.conf` walking up from `from`. */
  def find(from: Path): Option[Path] =
    Iterator
      .iterate(from.toAbsolutePath)(_.getParent)
      .takeWhile(_ != null)
      .map(_.resolve(FileName))
      .find(Files.isRegularFile(_))

  /** Reads and parses a `.meltfmt.conf` (HOCON). Missing keys use defaults.
    * Returns `Left(message)` on malformed HOCON or unresolved substitutions.
    */
  def load(path: Path): Either[String, MeltFmtConfig] =
    Try {
      // parseFile resolves a bare `include "..."` relative to this file's dir;
      // resolve() applies any ${...} substitutions.
      val cfg = ConfigFactory.parseFile(path.toFile).resolve()

      val indent =
        if cfg.hasPath("melt.css.indent") then cfg.getInt("melt.css.indent") else 2
      val selectorList =
        if cfg.hasPath("melt.css.selectorList") then
          cfg.getString("melt.css.selectorList").trim.toLowerCase match
            case "single-line" | "singleline" => SelectorListStyle.SingleLine
            case _                            => SelectorListStyle.Newline
        else SelectorListStyle.Newline

      val scriptIndent =
        if cfg.hasPath("melt.script.indent") then cfg.getInt("melt.script.indent") else 2

      val templateIndent =
        if cfg.hasPath("melt.template.indent") then cfg.getInt("melt.template.indent") else 2
      val templateContent =
        if cfg.hasPath("melt.template.content") then
          cfg.getString("melt.template.content").trim.toLowerCase match
            case "expanded" | "expand" => TemplateContentStyle.Expanded
            case _                     => TemplateContentStyle.Inline
        else TemplateContentStyle.Inline

      MeltFmtConfig(
        CssFormatOptions(indent, selectorList),
        ScriptFormatOptions(scriptIndent),
        TemplateFormatOptions(templateIndent, templateContent)
      )
    }.toEither.left.map(e => s"$path: ${ e.getMessage }")

  /** Discovers a `.meltfmt.conf` from `from` and loads it; defaults if none. */
  def loadFrom(from: Path): Either[String, MeltFmtConfig] =
    find(from) match
      case None    => Right(default)
      case Some(p) => load(p)
