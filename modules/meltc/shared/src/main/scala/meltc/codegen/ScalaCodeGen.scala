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
  *   - `create(): dom.Element` — builds and returns the DOM tree with reactive bindings
  *   - `mount(target: dom.Element): Unit` — appends the component into `target`
  */
object ScalaCodeGen:

  // ── Public API ─────────────────────────────────────────────────────────────

  /** Generates a scope ID from the component name (deterministic hash). */
  def scopeIdFor(objectName: String): String =
    val hash = objectName.foldLeft(17)((acc, c) => acc * 31 + c.toInt)
    f"melt-${ (hash & 0x7fffffff) % 0xffffff }%06x"

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

    // ── User script code ─────────────────────────────────────────────────────
    ast.script.foreach { sc =>
      if sc.code.nonEmpty then
        sc.code.linesIterator.foreach(line => buf ++= s"  $line\n")
        buf += '\n'
    }

    // ── create() ─────────────────────────────────────────────────────────────
    buf ++= "  def create(): dom.Element = {\n"
    buf ++= "    Cleanup.pushScope()\n"

    if ast.style.isDefined then buf ++= "    Style.inject(_scopeId, _css)\n"

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

    buf ++= "  def mount(target: dom.Element): Unit = Mount(target, create())\n\n"
    buf ++= "}\n"
    buf.toString

  // ── Node emission ──────────────────────────────────────────────────────────

  /** Emits statements that build `node` and returns the variable name holding it.
    * Returns `""` if the node produces no DOM node (blank text, unimplemented component)
    * or if the node was already appended to `parentVar` (e.g. by `Bind.text`).
    *
    * @param parentVar variable name of the parent DOM element, if available.
    *                  Used by Expression nodes to emit `Bind.text(expr, parent)`.
    */
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
            // Reactive binding — Bind.text creates the node and appends it to parent.
            // Overload resolution picks Var/Signal-aware overload when applicable,
            // otherwise falls back to Any (static text).
            buf ++= s"${ indent }Bind.text($code, $parent)\n"
            "" // already appended by Bind.text
          case None =>
            // Root-level expression without a parent — fall back to static text node
            val v = ctr.nextTxt()
            buf ++= s"""${ indent }val $v = dom.document.createTextNode(($code).toString)\n"""
            v

      case TemplateNode.Component(_, _, _) =>
        // Phase 5: not yet implemented
        ""

  // ── Attribute emission ─────────────────────────────────────────────────────

  private def emitAttr(buf: StringBuilder, v: String, attr: Attr, indent: String): Unit =
    attr match
      case Attr.Static("class", value) =>
        // Use classList.add to avoid overwriting the scope ID already added via classList.add(_scopeId)
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
      // Phase 5/6: not yet implemented

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
