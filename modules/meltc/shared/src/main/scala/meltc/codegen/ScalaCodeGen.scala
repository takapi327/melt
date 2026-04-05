/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.*

/** Generates Scala.js source code from a parsed [[meltc.ast.MeltFile]].
  *
  * Phase 3 scope: static DOM construction only (no reactive bindings, no components).
  * Each `.melt` file becomes a Scala `object` with:
  *   - `create(): dom.Element` — builds and returns the DOM tree
  *   - `mount(target: dom.Element): Unit` — appends the component into `target`
  */
object ScalaCodeGen:

  // ── Public API ─────────────────────────────────────────────────────────────

  /** Generates a scope ID from the component name (deterministic hash). */
  def scopeIdFor(objectName: String): String =
    val hash = objectName.foldLeft(17)((acc, c) => acc * 31 + c.toInt)
    f"melt-${ math.abs(hash) % 0xffffff }%06x"

  /** Compiles a [[meltc.ast.MeltFile]] into a Scala source string.
    *
    * @param ast        the parsed AST
    * @param objectName the generated object name (e.g. `"App"`)
    * @param pkg        the Scala package (may be empty)
    * @param scopeId    the scope class added to every DOM element for CSS isolation
    */
  def generate(ast: meltc.ast.MeltFile, objectName: String, pkg: String, scopeId: String): String =
    val buf = new StringBuilder
    val ctr = new Counter

    if pkg.nonEmpty then buf ++= s"package $pkg\n\n"

    buf ++= "import org.scalajs.dom\n"
    buf ++= "import melt.runtime.{ Mount, Style }\n\n"

    buf ++= s"object $objectName {\n\n"
    buf ++= s"""  private val _scopeId = "$scopeId"\n\n"""

    // ── CSS ──────────────────────────────────────────────────────────────────
    ast.style.foreach { s =>
      val css = s.css.replace("\\", "\\\\").replace("\"\"\"", "\\\"\\\"\\\"")
      buf ++= s"""  private val _css =\n    \"\"\"$css\"\"\"\n\n"""
    }

    // ── User script code ─────────────────────────────────────────────────────
    ast.script.foreach { sc =>
      if sc.code.nonEmpty then
        sc.code.linesIterator.foreach(line => buf ++= s"  $line\n")
        buf += '\n'
    }

    // ── create() ─────────────────────────────────────────────────────────────
    buf ++= "  def create(): dom.Element = {\n"

    if ast.style.isDefined then buf ++= "    Style.inject(_scopeId, _css)\n"

    val roots = ast.template.filter {
      case TemplateNode.Text(t) => !t.isBlank
      case _                    => true
    }

    roots match
      case Nil =>
        buf ++= "    dom.document.createElement(\"div\")\n"
      case single :: Nil =>
        val v = emitNode(buf, single, "    ", ctr, isRoot = true)
        if v.nonEmpty then buf ++= s"    $v\n"
      case multiple =>
        buf ++= "    val _root = dom.document.createElement(\"div\")\n"
        buf ++= "    _root.classList.add(_scopeId)\n"
        multiple.foreach { node =>
          val v = emitNode(buf, node, "    ", ctr, isRoot = false)
          if v.nonEmpty then buf ++= s"    _root.appendChild($v)\n"
        }
        buf ++= "    _root\n"

    buf ++= "  }\n\n"

    buf ++= "  def mount(target: dom.Element): Unit = Mount(target, create())\n\n"
    buf ++= "}\n"
    buf.toString

  // ── Node emission ──────────────────────────────────────────────────────────

  /** Emits statements that build `node` and returns the variable name holding it.
    * Returns `""` if the node produces no DOM node (blank text, unimplemented component).
    */
  private def emitNode(
    buf:    StringBuilder,
    node:   TemplateNode,
    indent: String,
    ctr:    Counter,
    isRoot: Boolean
  ): String =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        val v = ctr.nextEl()
        buf ++= s"""${ indent }val $v = dom.document.createElement("$tag")\n"""
        buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        attrs.foreach(emitAttr(buf, v, _, indent))
        children.foreach { child =>
          val cv = emitNode(buf, child, indent, ctr, isRoot = false)
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
        // Phase 3: static render — emit expression result as text
        val v = ctr.nextTxt()
        buf ++= s"""${ indent }val $v = dom.document.createTextNode(($code).toString)\n"""
        v

      case TemplateNode.Component(_, _, _) =>
        // Phase 5: not yet implemented
        ""

  // ── Attribute emission ─────────────────────────────────────────────────────

  private def emitAttr(buf: StringBuilder, v: String, attr: Attr, indent: String): Unit =
    attr match
      case Attr.Static(name, value) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", "${ escapeString(value) }")\n"""
      case Attr.BooleanAttr(name) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", "")\n"""
      case Attr.Dynamic(name, expr) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", ($expr).toString)\n"""
      case Attr.EventHandler(event, expr) =>
        buf ++= s"""${ indent }$v.addEventListener("$event", $expr)\n"""
      case Attr.Directive(_, _, _) | Attr.Spread(_) | Attr.Shorthand(_) =>
      // Phase 4/5/6: not yet implemented

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def escapeString(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private final class Counter:
    private var el  = 0
    private var txt = 0
    def nextEl():  String = { val v = s"_el$el"; el += 1; v }
    def nextTxt(): String = { val v = s"_txt$txt"; txt += 1; v }
