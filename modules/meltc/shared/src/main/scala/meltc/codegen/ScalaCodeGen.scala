/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.*

/** Generates Scala.js source code from a parsed [[meltc.ast.MeltFile]].
  *
  * Each `.melt` file becomes a Scala `object` with:
  *   - `create(): dom.Element` or `create(props: Props): dom.Element`
  *   - `mount(target: dom.Element): Unit` or `mount(target: dom.Element, props: Props): Unit`
  */
object ScalaCodeGen:

  // ── Public API ─────────────────────────────────────────────────────────────

  /** Generates a scope ID from the component name (deterministic hash). */
  def scopeIdFor(objectName: String): String =
    val hash = objectName.foldLeft(17)((acc, c) => acc * 31 + c.toInt)
    f"melt-${ (hash & 0x7fffffff) % 0xffffff }%06x"

  /** Compiles a [[meltc.ast.MeltFile]] into a Scala source string. */
  def generate(ast: meltc.ast.MeltFile, objectName: String, pkg: String, scopeId: String): String =
    val buf = new StringBuilder
    val ctr = new Counter

    if pkg.nonEmpty then buf ++= s"package $pkg\n\n"

    buf ++= "import org.scalajs.dom\n"
    buf ++= "import melt.runtime.{ Bind, Cleanup, Mount, Style, Var, Signal }\n"
    buf ++= "import melt.runtime.*\n\n"

    buf ++= s"object $objectName {\n\n"
    buf ++= s"""  private val _scopeId = "$scopeId"\n\n"""

    // ── CSS ──────────────────────────────────────────────────────────────────
    ast.style.foreach { s =>
      val scoped = CssScoper.scope(s.css, scopeId)
      val css    = escapeString(scoped)
      buf ++= s"""  private val _css =\n    "$css"\n\n"""
    }

    // ── Props type definition (object level) ─────────────────────────────────
    val propsType = ast.script.flatMap(_.propsType)
    ast.script.foreach { sc =>
      if sc.code.nonEmpty then
        propsType match
          case Some(typeName) =>
            val (propsDef, _) = splitPropsDefinition(sc.code, typeName)
            if propsDef.nonEmpty then
              propsDef.linesIterator.foreach(line => buf ++= s"  $line\n")
              buf += '\n'
          case None => // no props definition to emit at object level
    }

    // ── create() ─────────────────────────────────────────────────────────────
    propsType match
      case Some(typeName) =>
        buf ++= s"  def create(props: $typeName): dom.Element = {\n"
      case None =>
        buf ++= "  def create(): dom.Element = {\n"

    buf ++= "    Cleanup.pushScope()\n"

    if ast.style.isDefined then buf ++= "    Style.inject(_scopeId, _css)\n"

    // ── User script code (inside create for per-instance state) ──────────────
    ast.script.foreach { sc =>
      if sc.code.nonEmpty then
        val bodyCode = propsType match
          case Some(typeName) => splitPropsDefinition(sc.code, typeName)._2
          case None           => sc.code
        if bodyCode.trim.nonEmpty then
          bodyCode.linesIterator.foreach(line => buf ++= s"    $line\n")
          buf += '\n'
    }

    // ── Template ─────────────────────────────────────────────────────────────
    val roots = ast.template.filter {
      case TemplateNode.Text(t) => !t.isBlank
      case _                    => true
    }

    roots match
      case Nil =>
        buf ++= "    val _result = dom.document.createElement(\"div\")\n"
      case single :: Nil =>
        val v = emitNode(buf, single, "    ", ctr, isRoot = true, parentVar = None)
        if v.nonEmpty then buf ++= s"    val _result = $v\n"
        else buf ++= "    val _result = dom.document.createElement(\"div\")\n"
      case multiple =>
        buf ++= "    val _root = dom.document.createElement(\"div\")\n"
        buf ++= "    _root.classList.add(_scopeId)\n"
        multiple.foreach { node =>
          val v = emitNode(buf, node, "    ", ctr, isRoot = false, parentVar = Some("_root"))
          if v.nonEmpty then buf ++= s"    _root.appendChild($v)\n"
        }
        buf ++= "    val _result = _root\n"

    buf ++= "    val _cleanups = Cleanup.popScope()\n"
    buf ++= "    _result\n"
    buf ++= "  }\n\n"

    // ── mount() ──────────────────────────────────────────────────────────────
    propsType match
      case Some(typeName) =>
        buf ++= s"  def mount(target: dom.Element, props: $typeName): Unit = Mount(target, create(props))\n\n"
      case None =>
        buf ++= "  def mount(target: dom.Element): Unit = Mount(target, create())\n\n"

    buf ++= "}\n"
    buf.toString

  // ── Node emission ──────────────────────────────────────────────────────────

  private def emitNode(
    buf:       StringBuilder,
    node:      TemplateNode,
    indent:    String,
    ctr:       Counter,
    isRoot:    Boolean,
    parentVar: Option[String]
  ): String =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        val v = ctr.nextEl()
        buf ++= s"""${ indent }val $v = dom.document.createElement("$tag")\n"""
        buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        attrs.foreach(emitAttr(buf, v, _, indent))
        children.foreach { child =>
          val cv = emitNode(buf, child, indent, ctr, isRoot = false, parentVar = Some(v))
          if cv.nonEmpty then buf ++= s"${ indent }$v.appendChild($cv)\n"
        }
        v

      case TemplateNode.Text(content) =>
        if content.isBlank then ""
        else
          val v       = ctr.nextTxt()
          val escaped = escapeString(content)
          buf ++= s"""${ indent }val $v = dom.document.createTextNode("$escaped")\n"""
          v

      case TemplateNode.Expression(code) =>
        parentVar match
          case Some(parent) =>
            buf ++= s"${ indent }Bind.text($code, $parent)\n"
            ""
          case None =>
            val v = ctr.nextTxt()
            buf ++= s"""${ indent }val $v = dom.document.createTextNode(($code).toString)\n"""
            v

      case TemplateNode.Component(name, attrs, children) =>
        val v = ctr.nextEl()

        // Check for spread attribute
        val spreadExpr = attrs.collectFirst { case Attr.Spread(expr) => expr }

        // Check for `styled` attribute
        val hasStyled = attrs.exists {
          case Attr.BooleanAttr("styled") => true
          case _                          => false
        }

        // Filter children
        val filteredChildren = children.filter {
          case TemplateNode.Text(t) => !t.isBlank
          case _                    => true
        }

        // Build create call
        spreadExpr match
          case Some(expr) =>
            buf ++= s"${ indent }val $v = $name.create($expr)\n"
          case None =>
            val propsArgs = buildPropsArgs(attrs, filteredChildren, indent, ctr, buf)
            if propsArgs.nonEmpty then buf ++= s"${ indent }val $v = $name.create($name.Props($propsArgs))\n"
            else buf ++= s"${ indent }val $v = $name.create()\n"

        if hasStyled then buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        v

  // ── Attribute emission ─────────────────────────────────────────────────────

  private def emitAttr(buf: StringBuilder, v: String, attr: Attr, indent: String): Unit =
    attr match
      case Attr.Static("class", value) =>
        value.split("\\s+").filter(_.nonEmpty).foreach { cls =>
          buf ++= s"""${ indent }$v.classList.add("${ escapeString(cls) }")\n"""
        }
      case Attr.Static(name, value) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", "${ escapeString(value) }")\n"""
      case Attr.BooleanAttr(name) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", "")\n"""
      case Attr.Dynamic("class", expr) =>
        buf ++= s"""${ indent }($expr).toString.split("\\\\s+").filter(_.nonEmpty).foreach($v.classList.add(_))\n"""
      case Attr.Dynamic(name, expr) =>
        buf ++= s"""${ indent }Bind.attr($v, "$name", $expr)\n"""
      case Attr.EventHandler(event, expr) =>
        buf ++= s"""${ indent }$v.addEventListener("$event", $expr)\n"""
      case Attr.Directive("bind", "value", Some(expr)) =>
        buf ++= s"""${ indent }Bind.inputValue($v.asInstanceOf[dom.html.Input], $expr)\n"""
      case Attr.Directive(_, _, _) | Attr.Spread(_) | Attr.Shorthand(_) =>
      // Spread/Shorthand handled at component level; directives deferred to Phase 6

  // ── Component props building ───────────────────────────────────────────────

  /** Builds the Props constructor argument string from component attributes and children. */
  private def buildPropsArgs(
    attrs:    List[Attr],
    children: List[TemplateNode],
    indent:   String,
    ctr:      Counter,
    buf:      StringBuilder
  ): String =
    val args = List.newBuilder[String]

    attrs.foreach {
      case Attr.Static(name, value) =>
        args += s"""$name = "${ escapeString(value) }""""
      case Attr.Dynamic(name, expr) =>
        args += s"$name = $expr"
      case Attr.Shorthand(varName) =>
        args += s"$varName = $varName"
      case Attr.EventHandler(event, expr) =>
        // On components, event handlers become props (e.g., onAdd = handler)
        val propName = s"on${ event.charAt(0).toUpper }${ event.substring(1) }"
        args += s"$propName = $expr"
      case Attr.BooleanAttr("styled") =>
      // handled separately, not a prop
      case Attr.BooleanAttr(name) =>
        args += s"$name = true"
      case _ => // Spread handled at caller; Directive not a prop
    }

    // Children as a `() => dom.Element` prop
    if children.nonEmpty then
      val childVar = emitChildrenLambda(children, indent, ctr, buf)
      args += s"children = $childVar"

    args.result().mkString(", ")

  /** Emits a `val _childrenN = () => { ... }` lambda that builds the children DOM tree.
    * Returns the variable name (e.g. `_children0`).
    */
  private def emitChildrenLambda(
    children: List[TemplateNode],
    indent:   String,
    ctr:      Counter,
    buf:      StringBuilder
  ): String =
    val childCtr = new Counter
    val varName  = s"_children${ ctr.nextChildIdx() }"
    val inner    = indent + "  "

    buf ++= s"${ indent }val $varName: (() => dom.Element) = () => {\n"

    children match
      case single :: Nil =>
        val cv = emitNode(buf, single, inner, childCtr, isRoot = false, parentVar = None)
        if cv.nonEmpty then buf ++= s"${ inner }$cv\n"
        else buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case multiple =>
        buf ++= s"${ inner }val _frag = dom.document.createElement(\"div\")\n"
        multiple.foreach { child =>
          val cv = emitNode(buf, child, inner, childCtr, isRoot = false, parentVar = Some("_frag"))
          if cv.nonEmpty then buf ++= s"${ inner }_frag.appendChild($cv)\n"
        }
        buf ++= s"${ inner }_frag\n"

    buf ++= s"${ indent }}\n"
    varName

  // ── Props definition splitting ─────────────────────────────────────────────

  /** Splits script code into (propsDefinition, bodyCode).
    * The props definition (e.g. `case class Props(...)`) goes at object level;
    * everything else goes inside `create()`.
    */
  private def splitPropsDefinition(code: String, propsTypeName: String): (String, String) =
    val lines      = code.linesWithSeparators.toArray
    val propsDef   = new StringBuilder
    val bodyCode   = new StringBuilder
    var i          = 0
    var inPropsDef = false
    var depth      = 0

    while i < lines.length do
      val line    = lines(i)
      val trimmed = line.trim

      if !inPropsDef && trimmed.startsWith(s"case class $propsTypeName") then
        inPropsDef = true
        depth      = 0
        // Count parens to find end of case class
        for c <- line do
          if c == '(' then depth += 1
          else if c == ')' then depth -= 1
        propsDef ++= line
        if depth <= 0 then inPropsDef = false
      else if inPropsDef then
        for c <- line do
          if c == '(' then depth += 1
          else if c == ')' then depth -= 1
        propsDef ++= line
        if depth <= 0 then inPropsDef = false
      else bodyCode ++= line

      i += 1

    (propsDef.toString.trim, bodyCode.toString)

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def escapeString(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private final class Counter:
    private var el       = 0
    private var txt      = 0
    private var childIdx = 0
    def nextEl():       String = { val v = s"_el$el"; el += 1; v }
    def nextTxt():      String = { val v = s"_txt$txt"; txt += 1; v }
    def nextChildIdx(): Int    = { val v = childIdx; childIdx += 1; v }
