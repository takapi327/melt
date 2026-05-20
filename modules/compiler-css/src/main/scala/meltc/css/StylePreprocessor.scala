/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.css

/** Preprocesses a stylesheet before CSS scoping.
  *
  * Implementations compile source languages (e.g. SCSS) to plain CSS.
  * The resulting CSS is then passed to the CSS scoper.
  *
  * To add SCSS support, set `meltcSassEnabled := true` in `build.sbt`.
  * The `sbt-meltc` plugin will automatically add `meltc-sass` to the
  * compiler classpath and enable [[meltc.sass.SassPreprocessor]].
  */
trait StylePreprocessor:

  /** Compile `content` written in `lang` into plain CSS.
    *
    * @param content raw stylesheet text
    * @param lang    source language
    * @return Right(css) on success, Left(errorMessage) on failure
    */
  def process(content: String, lang: StyleLang): Either[String, String]

object StylePreprocessor:

  /** Pass-through implementation that accepts only plain CSS.
    *
    * SCSS (or any non-CSS lang) input results in a Left with a helpful
    * error message pointing to `meltcSassEnabled`.
    * This is the default used when no preprocessor plugin is available.
    */
  val cssOnly: StylePreprocessor = new StylePreprocessor:
    def process(content: String, lang: StyleLang): Either[String, String] =
      lang match
        case StyleLang.Css  => Right(content)
        case StyleLang.Scss =>
          Left(
            "SCSS is not supported. " +
              "Add `meltcSassEnabled := true` to build.sbt to enable SCSS support."
          )
