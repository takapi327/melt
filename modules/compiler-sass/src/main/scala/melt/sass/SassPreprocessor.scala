/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sass

import melt.css.{ StyleLang, StylePreprocessor }

import de.larsgrefer.sass.embedded.SassCompilerFactory

/** [[StylePreprocessor]] implementation using Dart Sass (embedded).
  *
  * Automatically activated when `meltStylePreprocessor := true` in `build.sbt`.
  * The `sbt-melt` plugin adds this module to the compiler classpath and
  * [[melt.MeltMain]] loads it dynamically via reflection.
  */
object SassPreprocessor extends StylePreprocessor:

  def process(content: String, lang: StyleLang): Either[String, String] =
    lang match
      case StyleLang.Css  => Right(content)
      case StyleLang.Scss =>
        val compiler = SassCompilerFactory.bundled()
        try Right(compiler.compileScssString(content).getCss)
        catch case e: Exception => Left(s"SCSS compilation failed: ${ e.getMessage }")
        finally compiler.close()
