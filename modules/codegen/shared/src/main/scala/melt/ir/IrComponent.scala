/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

/** The top-level IR node representing a compiled `.melt` component.
  *
  * Produced by [[AstToIr.lower]] from a [[melt.ast.MeltFile]].
  * Consumed by [[melt.emit.SpaEmitter]] and [[melt.emit.SsrEmitter]].
  */
case class IrComponent(
  objectName:        String,
  pkg:               String,
  scopeId:           String,
  propsType:         Option[IrPropsType],
  scriptBody:        String, // Scala code (verbatim, not parsed)
  moduleBody:        String              = "",  // <script lang="scala" module> content (emitted at object level)
  fileImports:       List[String], // import "path/to/style.css"
  typeDecls:         List[String], // top-level type declarations (SSR: hoisted out of apply())
  style:             Option[IrStyle],
  template:          List[IrNode],
  hoistedNodes:      List[IrHoistedNode] = Nil, // populated by StaticHoistPass
  hydration:         Boolean,
  sourcePath:        String,
  sourceMap:         IrSourceMap,
  scriptBodyLine:    Int                 = 1,   // 1-based line of script body start (for source-map)
  templateStartLine: Int                 = 1,   // 1-based line of template start (for source-map)
  nodePositions: IrNodePositions = IrNodePositions.empty, // per-node source positions built by AstToIr
  reactiveVars:  Set[String]     = Set.empty              // State/Signal/memo vars from the script section
)

/** Maps [[IrNode]] instances (by reference identity) to their source (line, col).
  * Built by [[AstToIr.lower]] and consumed by [[melt.emit.SpaEmitter]].
  *
  * Reference-identity semantics (via [[java.util.IdentityHashMap]]) mean that
  * two structurally equal IrNodes created at different positions are tracked
  * separately, just like [[melt.NodePositions]] does for [[melt.ast.TemplateNode]].
  */
final class IrNodePositions(
  private val underlying: java.util.IdentityHashMap[AnyRef, (Int, Int)]
):
  def get(node: IrNode): Option[(Int, Int)] =
    val pos = underlying.get(node)
    if pos == null then None else Some(pos)

object IrNodePositions:
  val empty:     IrNodePositions = new IrNodePositions(new java.util.IdentityHashMap)
  def builder(): Builder         = new Builder

  final class Builder:
    private val map = new java.util.IdentityHashMap[AnyRef, (Int, Int)]
    def put(node: IrNode, line: Int, col: Int): Unit            = map.put(node, (line, col))
    def build():                                IrNodePositions = new IrNodePositions(map)

/** A static element that has been lifted to object level by [[melt.ir.opt.StaticHoistPass]].
  * The [[melt.emit.SpaEmitter]] emits:
  *   - `private val _hoist_N = <original element construction>` at object level (once)
  *   - `_hoist_N.cloneNode(true)` at each call site ([[IrNode.IrHoistRef]])
  *
  * Cloning is required because each mounted component instance needs its own DOM node.
  */
case class IrHoistedNode(id: String, node: IrNode.IrStaticElement)

case class IrPropsType(
  typeName:         String,
  typeParams:       String, // e.g. "[A]" or ""
  baseName:         String, // e.g. "Props" or "Config"
  allHaveDefaults:  Boolean, // used for hydration fallback
  scriptDecl:       String, // the props declaration text
  isNamedTuple:     Boolean                = false, // true when Props is a Named Tuple
  namedTupleFields: List[(String, String)] = Nil    // (fieldName, typeStr) for factory generation
):

  /** Parsed fields of a `case class Props(...)` declaration, used by the emitters
    * to generate a field-forwarding `apply(field = ..., ...)` overload alongside
    * the `apply(props: Props)` one.
    *
    * Returns `Nil` (feature disabled — falls back to `apply(props)` only) for
    * named tuples, generic Props, aliases, or when the declaration cannot be
    * parsed cleanly. The conservative fallback guarantees no invalid code is
    * ever emitted.
    */
  def fieldForwardApplyFields: List[IrPropsField] =
    if isNamedTuple || typeParams.nonEmpty then Nil
    else IrPropsType.parseCaseClassFields(scriptDecl)

/** A `case class Props` field: `name: tpe = default`. */
case class IrPropsField(name: String, tpe: String, default: Option[String])

object IrPropsType:

  /** Parses the parameter list of a `case class Props(...)` declaration.
    * Returns `Nil` if the declaration is not a parenthesised parameter list or
    * any parameter cannot be cleanly split into `name: type [= default]`.
    */
  def parseCaseClassFields(scriptDecl: String): List[IrPropsField] =
    val open = scriptDecl.indexOf('(')
    if open < 0 then Nil
    else
      val close = matchingParen(scriptDecl, open)
      if close <= open then Nil
      else
        val params = splitTopLevel(scriptDecl.substring(open + 1, close), ',').map(_.trim).filter(_.nonEmpty)
        val parsed = params.map(parseField)
        if params.isEmpty || parsed.contains(None) then Nil else parsed.flatten

  private def parseField(p: String): Option[IrPropsField] =
    val colon = p.indexOf(':') // name is a simple identifier — first ':' separates it
    if colon <= 0 then None
    else
      val name = p.substring(0, colon).trim
      val rest = p.substring(colon + 1).trim
      if !isSimpleIdent(name) then None
      else
        val eq = topLevelAssignIndex(rest)
        if eq < 0 then Some(IrPropsField(name, rest, None))
        else
          val tpe  = rest.substring(0, eq).trim
          val dflt = rest.substring(eq + 1).trim
          if tpe.isEmpty || dflt.isEmpty then None else Some(IrPropsField(name, tpe, Some(dflt)))

  private def isSimpleIdent(s: String): Boolean =
    s.nonEmpty && (s.head.isLetter || s.head == '_') && s.forall(c => c.isLetterOrDigit || c == '_')

  /** Index of the matching `)` for the `(` at `open`, or -1. */
  private def matchingParen(s: String, open: Int): Int =
    if open < 0 || open >= s.length || s.charAt(open) != '(' then -1
    else
      var depth = 0
      var i     = open
      while i < s.length do
        s.charAt(i) match
          case '(' => depth += 1
          case ')' =>
            depth -= 1
            if depth == 0 then return i
          case _ => ()
        i += 1
      -1

  /** Splits `s` on top-level (depth-0) occurrences of `sep`, respecting nesting
    * of `()`, `[]`, and `{}`.
    */
  private def splitTopLevel(s: String, sep: Char): List[String] =
    val out   = List.newBuilder[String]
    val sb    = new StringBuilder
    var depth = 0
    var i     = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '(' | '[' | '{'     => depth += 1; sb += c
        case ')' | ']' | '}'     => depth -= 1; sb += c
        case `sep` if depth == 0 => out += sb.toString; sb.clear()
        case _                   => sb += c
      i += 1
    out += sb.toString
    out.result()

  /** Index of the `=` that starts the default value (depth 0, and not part of
    * `==`, `=>`, `<=`, `>=`, or `!=`), or -1 if there is no default.
    */
  private def topLevelAssignIndex(s: String): Int =
    var depth = 0
    var i     = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '(' | '[' | '{'   => depth += 1
        case ')' | ']' | '}'   => depth -= 1
        case '=' if depth == 0 =>
          val prev = if i > 0 then s.charAt(i - 1) else ' '
          val next = if i + 1 < s.length then s.charAt(i + 1) else ' '
          val isOp = next == '=' || next == '>' || prev == '<' || prev == '>' || prev == '!' || prev == '='
          if !isOp then return i
        case _ => ()
      i += 1
    -1

case class IrStyle(
  scopedCss: String, // already CSS-scoped via CssScoper
  scopeId:   String
)

/** Lightweight source-position mapping for the emitted file.
  * Replaces the ad-hoc `LineTracker.linesMetadata()` string format.
  */
case class IrSourceMap(
  sourcePath: String,
  entries:    Vector[IrSourceMapEntry]
)

case class IrSourceMapEntry(
  generatedLine: Int,
  sourceLine:    Int,
  sourceColumn:  Int
)

object IrSourceMap:
  val empty: IrSourceMap = IrSourceMap("", Vector.empty)
