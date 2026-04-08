/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

import org.eclipse.lsp4j.*

/** Resolves "Go to Definition" for identifiers used in the HTML template section.
  *
  * When the cursor is inside a `{expr}` binding in the template, this finder
  * extracts the identifier at the cursor position and searches the script section
  * for a matching `val`, `def`, `var`, or `type` declaration.
  *
  * This provides basic definition navigation without requiring Metals.  Metals
  * covers the script section itself; this covers the template → script direction.
  *
  * ==Example==
  * {{{
  * // .melt source
  * <script lang="scala">
  *   val count = Var(0)         // ← line 1
  *   def increment() = ...     // ← line 2
  * </script>
  * <div>{count}</div>          // cursor on "count" → jumps to line 1
  * }}}
  */
object ScriptDefinitionFinder:

  /** Scala identifier characters: letters, digits, underscore, dollar. */
  private val IdentChar = """[\w$]""".r

  /** Matches `val name`, `def name`, `var name`, `type name`, `class name`, `object name`.
    * Group 1 = identifier name.
    */
  private val DefRe = """(?:val|def|var|type|class|object|given|enum)\s+([\w$]+)""".r

  /** Finds the definition location for the identifier at (line, character) in the template.
    *
    * @param meltSource full .melt source text
    * @param meltUri    file URI of the .melt document (used to build the returned [[Location]])
    * @param line       0-based line of the cursor (must be in the template section)
    * @param character  0-based character offset of the cursor
    * @param vf         [[VirtualFile]] for this document (provides [[PositionMapper]])
    * @return a single-element list with the definition [[Location]], or empty if not found
    */
  def find(
    meltSource: String,
    meltUri:    String,
    line:       Int,
    character:  Int,
    vf:         VirtualFile
  ): List[Location] =
    val lines = meltSource.split("\n", -1)
    extractIdentifier(lines, line, character) match
      case None       => Nil
      case Some(name) => searchScriptSection(lines, meltUri, name, vf)

  // ── Private helpers ───────────────────────────────────────────────────────

  /** Extracts the Scala identifier that the cursor is touching on the given line.
    * Returns None if the cursor is not on an identifier character.
    */
  private def extractIdentifier(lines: Array[String], line: Int, character: Int): Option[String] =
    if line >= lines.length then return None
    val text = lines(line)
    if character >= text.length then return None
    if !IdentChar.matches(text(character).toString) then return None

    var start = character
    var end   = character
    while start > 0 && IdentChar.matches(text(start - 1).toString) do start -= 1
    while end < text.length - 1 && IdentChar.matches(text(end + 1).toString) do end += 1

    val name = text.substring(start, end + 1)
    // Ignore pure-digit strings and Scala keywords
    if name.forall(_.isDigit) || ScalaKeywords.contains(name) then None
    else Some(name)

  /** Searches every line of the script section for a definition of [name].
    * Returns a [[Location]] pointing to the character after the keyword (i.e. the name itself).
    */
  private def searchScriptSection(
    lines:   Array[String],
    meltUri: String,
    name:    String,
    vf:      VirtualFile
  ): List[Location] =
    lines.zipWithIndex
      .flatMap { (line, idx) =>
        if !vf.mapper.isScriptLine(idx) then None
        else
          DefRe.findAllMatchIn(line).collectFirst {
            case m if m.group(1) == name =>
              val startChar = m.start(1)
              val range     = Range(Position(idx, startChar), Position(idx, startChar + name.length))
              Location(meltUri, range)
          }
      }
      .take(1)
      .toList

  private val ScalaKeywords = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "enum",
    "export",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "given",
    "if",
    "implicit",
    "import",
    "lazy",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "then",
    "this",
    "throw",
    "trait",
    "true",
    "try",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield"
  )
