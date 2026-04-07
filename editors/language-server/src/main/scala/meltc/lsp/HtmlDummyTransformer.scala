/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

/** Extracts Scala expressions from the HTML template section of a .melt file and
  * generates dummy Scala code that references them.
  *
  * This allows Metals to type-check template expressions (`{expr}`) as part of the
  * virtual .scala file. Each extracted expression is placed inside a dummy private
  * object that is appended **after** the script section, so it does not disturb the
  * line-number alignment of the script body.
  *
  * Note: The dummy code is not required for basic LSP operation (diagnostics from
  * meltc already cover syntax errors). It becomes valuable when Metals delegation is
  * enabled so that type errors in template expressions are surfaced to the editor.
  */
object HtmlDummyTransformer:

  /** Matches a `{expression}` block in HTML. Nested braces are not supported. */
  private val ExpressionRe = """\{([^{}]+)\}""".r

  /** Extracts all `{expression}` code strings from the template source text. */
  def extractExpressions(templateSource: String): List[String] =
    ExpressionRe.findAllMatchIn(templateSource).map(_.group(1).trim).filter(_.nonEmpty).toList

  /** Generates a dummy Scala private object that references each template expression.
    *
    * The object is named `_<objectName>TemplateDummies` and contains one private `val`
    * per expression. Appending this to the virtual .scala file causes Metals to
    * type-check each expression in the context of the script section's definitions.
    *
    * @param expressions Scala expression strings extracted from the template
    * @param objectName  name of the component (used to build the dummy object name)
    * @return Scala source snippet ready to append to the virtual file, or empty string
    *         if there are no expressions
    */
  def generateDummyCode(expressions: List[String], objectName: String): String =
    if expressions.isEmpty then ""
    else
      val refs = expressions.zipWithIndex
        .map { (expr, i) => s"  private val _expr$i = { $expr }" }
        .mkString("\n")
      s"""
private object _${ objectName }TemplateDummies:
$refs
"""
