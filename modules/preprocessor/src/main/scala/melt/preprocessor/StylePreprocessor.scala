/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.preprocessor

/** Input type for stylesheet preprocessing. */
case class StyleInput(content: String, lang: StyleLang)

/** Preprocessor specialised for stylesheets.
  *
  * Implementations compile source languages (e.g. SCSS) to plain CSS.
  * The resulting CSS is then passed to the CSS scoper.
  *
  * To add SCSS support, set `meltStylePreprocessor := true` in `build.sbt`.
  * The `sbt-melt` plugin will automatically add `melt-sass-preprocessor` to the
  * compiler classpath and enable [[melt.sass.SassPreprocessor]].
  */
type StylePreprocessor = Preprocessor[StyleInput, String]

object StylePreprocessor:

  /** Pass-through implementation that accepts only plain CSS.
    *
    * SCSS (or any non-CSS lang) input results in a Left with a helpful
    * error message pointing to `meltStylePreprocessor`.
    * This is the default used when no preprocessor plugin is available.
    */
  val cssOnly: StylePreprocessor = new Preprocessor[StyleInput, String]:
    def process(input: StyleInput): Either[String, String] =
      input.lang match
        case StyleLang.Css  => Right(input.content)
        case StyleLang.Scss =>
          Left(
            "SCSS is not supported. " +
              "Add `meltStylePreprocessor := true` to build.sbt to enable SCSS support."
          )
