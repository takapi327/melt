/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import java.nio.file.{ Files, Path, Paths }

import scala.jdk.CollectionConverters.*

import com.typesafe.config.{ ConfigFactory, ConfigRenderOptions }

/** CLI entry point for the `.melt` formatter.
  *
  * {{{
  * MeltFmtMain [--check] [--config <path>] <file-or-dir>...
  * }}}
  *   - `--check`         verify only; throw (non-zero task exit) if any file would change
  *   - `--config <path>` explicit scalafmt config path (overrides discovery)
  *
  * A `.melt` file is configured by `.meltfmt.conf` (discovered by walking up from
  * the working directory). The `<script>` scalafmt *style* is resolved from that
  * same file — typically via `include ".scalafmt.conf"` — so a project only needs
  * one config for `.melt`. Resolution order for the scalafmt style:
  *   1. `--config <path>` (must exist);
  *   2. `.meltfmt.conf` if it carries a scalafmt config (a `version` key, e.g. via
  *      `include`) — the `melt.*` keys are stripped and the rest handed to scalafmt;
  *   3. a bare `.scalafmt.conf` in the working directory (backward compatible).
  * If none is found, `<script>` sections are left unchanged (CSS/template still run).
  *
  * Directories are scanned recursively for `*.melt`. Runs in-process, so it
  * throws (rather than `System.exit`) on failure to avoid killing a host sbt JVM.
  */
object MeltFmtMain:

  def main(args: Array[String]): Unit =
    val (check, configOpt, targets) = parse(args.toList)
    val cwd                         = Paths.get("").toAbsolutePath.resolve("_")

    val meltfmtPath = MeltFmtConfig.find(cwd)
    meltfmtPath.foreach(p => println(s"[meltfmt] using $p"))
    // A malformed config fails the run loudly rather than silently falling back.
    val meltCfg = MeltFmtConfig.loadFrom(cwd) match
      case Right(c)  => c
      case Left(err) => throw new RuntimeException(s"[meltfmt] $err")

    val files = targets.flatMap(meltFiles).distinct
    if files.isEmpty then
      println("[meltfmt] no .melt files found")
      return

    val scalafmtConf = resolveScalafmtConfig(configOpt, meltfmtPath)
    val formatter    = scalafmtConf.map(new ScriptFormatter(_, meltCfg.script.indent))
    if formatter.isEmpty then
      System.err.println(
        "[meltfmt] no scalafmt config found — <script> sections will be left unchanged " +
          "(add `include \".scalafmt.conf\"` to .meltfmt.conf, or provide .scalafmt.conf)"
      )
    val cssOptions  = toCssOptions(meltCfg.css)
    val tmplOptions = toTemplateOptions(meltCfg.template)
    val unformatted = scala.collection.mutable.ListBuffer.empty[String] // fixable by meltFmt
    val skipped     = scala.collection.mutable.ListBuffer.empty[String] // unparseable — left as-is
    var changed     = 0

    files.foreach { f =>
      val src = Files.readString(f)
      MeltFmt.format(src, formatter, cssOptions, tmplOptions) match
        case Right(out) if out == src => ()
        case Right(_) if check        => unformatted += f.toString
        case Right(out)               =>
          Files.writeString(f, out); changed += 1; println(s"[meltfmt] formatted $f")
        case Left(err) => skipped += s"$f: $err" // never corrupt an unparseable file
    }

    // Unparseable files are warned about but never fail the run: meltFmt cannot
    // fix them and must not touch them (e.g. a `</script>` inside a script string).
    if skipped.nonEmpty then
      System.err.println(s"[meltfmt] skipped ${ skipped.size } unparseable file(s):")
      skipped.foreach(s => System.err.println("  " + s))

    if check then
      if unformatted.nonEmpty then
        throw new RuntimeException(
          s"[meltfmt] ${ unformatted.size } of ${ files.size } file(s) need formatting (run `sbt meltFmt`):\n" +
            unformatted.map("  " + _).mkString("\n")
        )
      else println(s"[meltfmt] check OK (${ files.size } file(s), ${ skipped.size } skipped)")
    else println(s"[meltfmt] formatted $changed of ${ files.size } file(s), ${ skipped.size } skipped")

  /** Resolves the scalafmt config for `<script>` formatting.
    *
    *   1. `--config` (must exist, else error — an explicit request that can't be met);
    *   2. `.meltfmt.conf` carrying a scalafmt config (`version` present, e.g. via
    *      `include ".scalafmt.conf"`) — strip `melt.*` and write the rest to a temp
    *      file scalafmt-dynamic can read;
    *   3. a bare `.scalafmt.conf`;
    *   otherwise `None` (scripts are skipped).
    */
  private def resolveScalafmtConfig(explicit: Option[Path], meltfmt: Option[Path]): Option[Path] =
    explicit match
      case Some(p) if Files.isRegularFile(p) => Some(p)
      case Some(p)                           => throw new RuntimeException(s"[meltfmt] --config not found: ${ p.toAbsolutePath }")
      case None                              =>
        scalafmtFromMeltfmt(meltfmt).orElse {
          val fallback = Paths.get(".scalafmt.conf")
          Option.when(Files.isRegularFile(fallback))(fallback)
        }

  /** If `.meltfmt.conf` resolves to a config that includes/inlines scalafmt
    * settings (detected by a top-level `version`), returns a temp file holding the
    * scalafmt config with the `melt.*` namespace removed (scalafmt-dynamic rejects
    * unknown keys). Returns `None` when no scalafmt config is present. */
  private def scalafmtFromMeltfmt(meltfmt: Option[Path]): Option[Path] =
    meltfmt.flatMap { mp =>
      val cfg = ConfigFactory.parseFile(mp.toFile).resolve()
      Option.when(cfg.hasPath("version")) {
        val scalafmt = cfg.withoutPath("melt")
        val rendered = scalafmt.root().render(
          ConfigRenderOptions.defaults().setOriginComments(false).setComments(false).setJson(false)
        )
        val tmp = Files.createTempFile("melt-scalafmt", ".conf")
        tmp.toFile.deleteOnExit()
        Files.writeString(tmp, rendered)
        tmp
      }
    }

  /** Maps the `.meltfmt.conf` CSS options onto the compiler's [[CssFormatter]]. */
  private def toCssOptions(css: CssFormatOptions): melt.css.CssFormatter.Options =
    val sel = css.selectorList match
      case SelectorListStyle.SingleLine => melt.css.CssFormatter.SelectorList.SingleLine
      case SelectorListStyle.Newline    => melt.css.CssFormatter.SelectorList.Newline
    melt.css.CssFormatter.Options(indent = css.indent, selectorList = sel)

  /** Maps the `.meltfmt.conf` template options onto the compiler's [[melt.template.TemplateFormatter]]. */
  private def toTemplateOptions(t: TemplateFormatOptions): melt.template.TemplateFormatter.Options =
    melt.template.TemplateFormatter.Options(
      indent        = t.indent,
      expandContent = t.content == TemplateContentStyle.Expanded
    )

  private def parse(args: List[String]): (Boolean, Option[Path], List[Path]) =
    def loop(
      as:    List[String],
      check: Boolean,
      cfg:   Option[Path],
      acc:   List[Path]
    ): (Boolean, Option[Path], List[Path]) =
      as match
        case Nil                     => (check, cfg, acc.reverse)
        case "--check" :: rest       => loop(rest, true, cfg, acc)
        case "--config" :: p :: rest => loop(rest, check, Some(Paths.get(p)), acc)
        case other :: rest           => loop(rest, check, cfg, Paths.get(other) :: acc)
    loop(args, false, None, Nil)

  private def meltFiles(target: Path): List[Path] =
    if Files.isRegularFile(target) then if target.toString.endsWith(".melt") then List(target) else Nil
    else if Files.isDirectory(target) then
      Files
        .walk(target)
        .iterator
        .asScala
        .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".melt"))
        .toList
    else Nil
