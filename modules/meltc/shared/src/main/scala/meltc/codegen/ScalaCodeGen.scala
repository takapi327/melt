/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.*
import meltc.ast.InlineTemplatePart

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
    buf ++= "import melt.runtime.{ Bind, Cleanup, Mount, Ref, Style, Var, Signal }\n"
    buf ++= "import melt.runtime.*\n"
    buf ++= "import melt.runtime.transition.*\n"
    buf ++= "import melt.runtime.animate.*\n\n"

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

    buf ++= "    val (_result, _owner) = Owner.withNew {\n"

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

    buf ++= "    _result\n"
    buf ++= "    }\n"
    buf ++= "    Lifecycle.register(_result, _owner)\n"
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
    parentVar: Option[String],
    ns:        String = "" // current namespace context: "" | "svg" | "math"
  ): String =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        val v = ctr.nextEl()
        // Determine namespace for this element and propagate to children.
        val childNs =
          if tag == "svg" || (ns == "svg" && KnownSvgTags.contains(tag)) then "svg"
          else if tag == "math" || (ns == "math" && KnownMathTags.contains(tag)) then "math"
          else ns
        if childNs == "svg" then buf ++= s"""${ indent }val $v = dom.document.createElementNS("$SvgNs", "$tag")\n"""
        else if childNs == "math" then
          buf ++= s"""${ indent }val $v = dom.document.createElementNS("$MathNs", "$tag")\n"""
        else buf ++= s"""${ indent }val $v = dom.document.createElement("$tag")\n"""
        buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        attrs.foreach(emitAttr(buf, v, _, indent, attrs))
        children.foreach { child =>
          val cv = emitNode(buf, child, indent, ctr, isRoot = false, parentVar = Some(v), ns = childNs)
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
        classifyExpr(code) match
          case ExprKind.ListMap =>
            // {source.map(renderFn)} → anchor + Bind.list
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            val dotMap = code.lastIndexOf(".map(")
            val source = code.substring(0, dotMap).trim
            val fnBody = code.substring(dotMap + 5, code.length - 1).trim // remove trailing ')'
            buf ++= s"${ indent }Bind.list($source, $fnBody, $anchor)\n"
            ""

          case ExprKind.KeyedMap =>
            // {source.keyed(keyFn).map(renderFn)} → anchor + Bind.each
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            val keyedIdx   = code.indexOf(".keyed(")
            val source     = code.substring(0, keyedIdx).trim
            val afterKeyed = code.substring(keyedIdx + 7)
            val keyEnd     = findBalancedParen(afterKeyed, 0)
            val keyFn      = afterKeyed.substring(0, keyEnd).trim
            val rest       = afterKeyed.substring(keyEnd + 1) // skip ')'
            val dotMap     = rest.indexOf(".map(")
            val fnBody     = rest.substring(dotMap + 5, rest.length - 1).trim
            buf ++= s"${ indent }Bind.each($source, $keyFn, $fnBody, $anchor)\n"
            ""

          case ExprKind.DomExpr =>
            // if/else or match returning DOM nodes — use Bind.show with anchor.
            // If the expression begins with `if <var>.now()`, emit the reactive overload so
            // that Bind.show re-renders whenever the referenced Var/Signal changes.
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            extractReactiveSource(code) match
              case Some(source) =>
                buf ++= s"${ indent }Bind.show($source, _ => { $code }, $anchor)\n"
              case None =>
                buf ++= s"${ indent }Bind.show(() => { $code }, $anchor)\n"
            ""

          case ExprKind.PlainText =>
            parentVar match
              case Some(parent) =>
                buf ++= s"${ indent }Bind.text($code, $parent)\n"
                ""
              case None =>
                val v = ctr.nextTxt()
                buf ++= s"""${ indent }val $v = dom.document.createTextNode(($code).toString)\n"""
                v

      case TemplateNode.InlineTemplate(parts) =>
        // Build a Scala expression by converting HTML parts to DOM construction code
        val exprBuf = new StringBuilder
        parts.foreach {
          case InlineTemplatePart.Code(code) =>
            exprBuf ++= code
          case InlineTemplatePart.Html(nodes) =>
            val innerBuf = new StringBuilder
            val innerCtr = new Counter
            nodes match
              case Nil =>
                exprBuf ++= s"dom.document.createTextNode(\"\")"
              case single :: Nil =>
                val v = emitNode(innerBuf, single, indent + "  ", innerCtr, isRoot = false, parentVar = None)
                exprBuf ++= s"{\n$innerBuf${ indent }  $v\n${ indent }}"
              case multiple =>
                innerBuf ++= s"${ indent }  val _frag = dom.document.createElement(\"div\")\n"
                multiple.foreach { n =>
                  val v =
                    emitNode(innerBuf, n, indent + "  ", innerCtr, isRoot = false, parentVar = Some("_frag"))
                  if v.nonEmpty then innerBuf ++= s"${ indent }  _frag.appendChild($v)\n"
                }
                exprBuf ++= s"{\n$innerBuf${ indent }  _frag\n${ indent }}"
        }
        // Re-emit as a regular Expression with the expanded code
        emitNode(buf, TemplateNode.Expression(exprBuf.toString), indent, ctr, isRoot, parentVar)

      case TemplateNode.Head(children) =>
        children.foreach { child =>
          val cv = emitNode(buf, child, indent, ctr, isRoot = false, parentVar = None)
          if cv.nonEmpty then buf ++= s"${ indent }Head.appendChild($cv)\n"
        }
        ""

      case TemplateNode.Window(attrs) =>
        attrs.foreach {
          case Attr.EventHandler(event, expr) =>
            buf ++= s"""${ indent }Window.on("$event")($expr)\n"""
          case Attr.Directive("bind", prop, Some(expr), _) =>
            val method = prop match
              case "scrollY"          => "bindScrollY"
              case "scrollX"          => "bindScrollX"
              case "innerWidth"       => "bindInnerWidth"
              case "innerHeight"      => "bindInnerHeight"
              case "outerWidth"       => "bindOuterWidth"
              case "outerHeight"      => "bindOuterHeight"
              case "devicePixelRatio" => "bindDevicePixelRatio"
              case "online"           => "bindOnline"
              case other              => s"bind${ other.capitalize }"
            buf ++= s"${ indent }Window.$method($expr)\n"
          case _ =>
        }
        ""

      case TemplateNode.Body(attrs) =>
        attrs.foreach {
          case Attr.EventHandler(event, expr) =>
            buf ++= s"""${ indent }Body.on("$event")($expr)\n"""
          case Attr.Directive("use", actionName, Some(expr), _) =>
            buf ++= s"${ indent }Bind.action(dom.document.body, $actionName, $expr)\n"
          case Attr.Directive("use", actionName, None, _) =>
            buf ++= s"${ indent }Bind.action(dom.document.body, $actionName, ())\n"
          case _ =>
        }
        ""

      case TemplateNode.DynamicElement(tagExpr, attrs, children) =>
        attrs.foreach {
          case Attr.Directive("animate", _, _, _) =>
            sys.error("`<melt:element>` does not support `animate:`")
          case _ =>
        }
        val anchor   = ctr.nextTxt()
        val elVar    = "_dynEl"
        val setupBuf = new StringBuilder
        attrs.foreach(emitAttr(setupBuf, elVar, _, s"$indent  ", attrs))
        children.foreach { child =>
          val cv = emitNode(setupBuf, child, s"$indent  ", ctr, isRoot = false, parentVar = Some(elVar))
          if cv.nonEmpty then setupBuf ++= s"$indent  $elVar.appendChild($cv)\n"
        }
        buf ++= s"""${ indent }val $anchor = dom.document.createComment("")\n"""
        parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
        buf ++= s"${ indent }Bind.dynamicElement($tagExpr, $anchor, _scopeId, ($elVar: dom.Element) => {\n"
        buf ++= setupBuf.toString
        buf ++= s"${ indent }  ()\n" // ensure the setup lambda returns Unit
        buf ++= s"${ indent }})\n"
        ""

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

  private def emitAttr(
    buf:      StringBuilder,
    v:        String,
    attr:     Attr,
    indent:   String,
    allAttrs: List[Attr] = Nil
  ): Unit =
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
      case Attr.Dynamic(name, expr) if ScalaCodeGen.htmlBooleanAttrs.contains(name) =>
        buf ++= s"""${ indent }Bind.booleanAttr($v, "$name", $expr)\n"""
      case Attr.Dynamic(name, expr) =>
        buf ++= s"""${ indent }Bind.attr($v, "$name", $expr)\n"""
      case Attr.EventHandler(event, expr) =>
        buf ++= s"""${ indent }$v.addEventListener("$event", $expr)\n"""
      case Attr.Directive("bind", "value", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.inputValue($v.asInstanceOf[dom.html.Input], $expr)\n"""
      case Attr.Directive("bind", "value-int", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.inputInt($v.asInstanceOf[dom.html.Input], $expr)\n"""
      case Attr.Directive("bind", "value-double", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.inputDouble($v.asInstanceOf[dom.html.Input], $expr)\n"""
      case Attr.Directive("bind", "checked", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.inputChecked($v.asInstanceOf[dom.html.Input], $expr)\n"""
      case Attr.Directive("bind", "group", Some(expr), _) =>
        val isCheckbox = allAttrs.exists {
          case Attr.Static("type", "checkbox") => true
          case _                               => false
        }
        if isCheckbox then
          buf ++= s"""${ indent }Bind.checkboxGroup($v.asInstanceOf[dom.html.Input], $expr, $v.asInstanceOf[dom.html.Input].value)\n"""
        else
          buf ++= s"""${ indent }Bind.radioGroup($v.asInstanceOf[dom.html.Input], $expr, $v.asInstanceOf[dom.html.Input].value)\n"""
      case Attr.Directive("bind", "this", Some(expr), _) =>
        buf ++= s"""${ indent }$expr.set($v.asInstanceOf[dom.Element])\n"""
      case Attr.Directive("class", name, Some(expr), _) =>
        buf ++= s"""${ indent }Bind.classToggle($v, "$name", $expr)\n"""
      case Attr.Directive("class", name, None, _) =>
        buf ++= s"""${ indent }Bind.classToggle($v, "$name", $name)\n"""
      case Attr.Directive("style", property, Some(expr), _) =>
        buf ++= s"""${ indent }Bind.style($v, "$property", $expr)\n"""
      case Attr.Directive("use", actionName, Some(expr), _) =>
        buf ++= s"""${ indent }Bind.action($v, $actionName, $expr)\n"""
      case Attr.Directive("use", actionName, None, _) =>
        buf ++= s"""${ indent }Bind.action($v, $actionName, ())\n"""
      case Attr.Directive("transition", transName, exprOpt, mods) =>
        val params = exprOpt.getOrElse("TransitionParams.default")
        val obj    = transName.capitalize
        buf ++= s"""${ indent }TransitionBridge.setBoth($v, $obj, $params)\n"""
        if mods.contains("global") then
          buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltGlobal")(true)\n"""
      case Attr.Directive("in", transName, exprOpt, mods) =>
        val params = exprOpt.getOrElse("TransitionParams.default")
        val obj    = transName.capitalize
        buf ++= s"""${ indent }TransitionBridge.setIn($v, $obj, $params)\n"""
        if mods.contains("global") then
          buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltGlobal")(true)\n"""
      case Attr.Directive("out", transName, exprOpt, mods) =>
        val params = exprOpt.getOrElse("TransitionParams.default")
        val obj    = transName.capitalize
        buf ++= s"""${ indent }TransitionBridge.setOut($v, $obj, $params)\n"""
        if mods.contains("global") then
          buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltGlobal")(true)\n"""
      case Attr.Directive("animate", animName, exprOpt, _) =>
        val fn     = animName.capitalize
        val params = exprOpt.getOrElse("AnimateParams()")
        // Type ascriptions ensure the Scala compiler rejects wrong types at compile time:
        //   ($fn: AnimateFn)      — non-AnimateFn values are rejected before erasure
        //   ($params: AnimateParams) — wrong param types (e.g. TransitionParams) are caught
        buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltAnimateFn")(($fn: AnimateFn).asInstanceOf[scalajs.js.Any])\n"""
        buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltAnimateParams")(($params: AnimateParams).asInstanceOf[scalajs.js.Any])\n"""
      case Attr.Spread(expr) =>
        // On HTML elements: apply HtmlAttrs spread
        buf ++= s"""${ indent }$expr.apply($v)\n"""
      case Attr.Directive(_, _, _, _) | Attr.Shorthand(_) =>
      // Shorthand handled at component level; remaining directives deferred

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

  // ── Expression classification ───────────────────────────────────────────

  private enum ExprKind:
    case ListMap   // contains ".map(" with DOM body — list rendering
    case KeyedMap  // contains ".keyed(" ... ".map(" — keyed list
    case DomExpr   // if/else or match that produces DOM nodes
    case PlainText // everything else — rendered as text via Bind.text

  /** Classifies a template expression to determine rendering strategy.
    *
    * A `.map(` expression is classified as list rendering only if the map
    * body appears to produce DOM nodes (contains `createElement`, multi-line
    * block, or explicit `: dom.Node` type annotation). Simple `.map(_.size)`
    * style expressions remain as plain text (Bind.text).
    */
  /** Checks whether the expression produces DOM nodes (contains inline HTML or createElement). */
  private def containsDomConstruction(code: String): Boolean =
    code.contains("createElement") ||
      code.contains("dom.document") ||
      code.contains(": dom.Node") ||
      code.contains(": dom.Element") ||
      code.count(_ == '\n') > 1

  private def classifyExpr(code: String): ExprKind =
    val trimmed = code.trim
    if trimmed.contains(".keyed(") && trimmed.contains(".map(") then ExprKind.KeyedMap
    else if trimmed.contains(".map(") then
      val dotMap  = trimmed.lastIndexOf(".map(")
      val mapBody = trimmed.substring(dotMap + 5)
      if containsDomConstruction(mapBody) then ExprKind.ListMap else ExprKind.PlainText
    else if (trimmed.startsWith("if ") || trimmed.startsWith("if(")) && containsDomConstruction(trimmed) then
      ExprKind.DomExpr
    else if trimmed.contains(" match") && containsDomConstruction(trimmed) then ExprKind.DomExpr
    else ExprKind.PlainText

  /** Attempts to extract a reactive source (Var or Signal identifier) from a conditional
    * DOM expression so the reactive `Bind.show(source, render, anchor)` overload can be used.
    *
    * Recognized patterns:
    *   - `if <ident>.now() then ...`       → `Some("<ident>")`
    *   - `if !<ident>.now() then ...`      → `Some("<ident>")`
    *   - `<ident> match { ... }` (match on a Var/Signal via .now()) → extracted identifier
    *
    * Returns `None` if the expression cannot be mapped to a single reactive source.
    */
  private def extractReactiveSource(code: String): Option[String] =
    val trimmed = code.trim
    // Pattern: if [!]<ident>.now() then ...
    val ifNowRe = """^if\s+!?([a-zA-Z_][a-zA-Z0-9_.]*)\.now\(\)""".r
    ifNowRe.findFirstMatchIn(trimmed).map(_.group(1))

  /** Finds the position of the closing `)` matching the first `(` in `s` starting at `start`. */
  private def findBalancedParen(s: String, start: Int): Int =
    var depth = 0
    var i     = start
    while i < s.length do
      if s(i) == '(' then depth += 1
      else if s(i) == ')' then
        depth -= 1
        if depth < 0 then return i
      i += 1
    i

  // ── Namespace constants ───────────────────────────────────────────────────

  private val SvgNs  = "http://www.w3.org/2000/svg"
  private val MathNs = "http://www.w3.org/1998/Math/MathML"

  /** SVG element names that must be created with `createElementNS`. */
  private val KnownSvgTags: Set[String] = Set(
    "animate",
    "animateMotion",
    "animateTransform",
    "circle",
    "clipPath",
    "defs",
    "desc",
    "ellipse",
    "feBlend",
    "feColorMatrix",
    "feComponentTransfer",
    "feComposite",
    "feConvolveMatrix",
    "feDiffuseLighting",
    "feDisplacementMap",
    "feFlood",
    "feGaussianBlur",
    "feImage",
    "feMerge",
    "feMorphology",
    "feOffset",
    "feSpecularLighting",
    "feTile",
    "feTurbulence",
    "filter",
    "foreignObject",
    "g",
    "image",
    "line",
    "linearGradient",
    "marker",
    "mask",
    "metadata",
    "mpath",
    "path",
    "pattern",
    "polygon",
    "polyline",
    "radialGradient",
    "rect",
    "set",
    "stop",
    "svg",
    "switch",
    "symbol",
    "text",
    "textPath",
    "title",
    "tspan",
    "use",
    "view"
  )

  /** MathML element names that must be created with `createElementNS`. */
  private val KnownMathTags: Set[String] = Set(
    "annotation",
    "annotation-xml",
    "math",
    "merror",
    "mfrac",
    "mi",
    "mn",
    "mo",
    "mover",
    "mpadded",
    "mphantom",
    "mroot",
    "mrow",
    "ms",
    "msqrt",
    "mspace",
    "mstyle",
    "msub",
    "msubsup",
    "msup",
    "mtable",
    "mtd",
    "mtext",
    "mtr",
    "munder",
    "munderover",
    "semantics"
  )

  // ── Known HTML boolean attributes ─────────────────────────────────────────
  // These attributes use presence/absence (not value) to mean true/false.
  // Dynamic bindings for these names emit Bind.booleanAttr instead of Bind.attr.
  val htmlBooleanAttrs: Set[String] = Set(
    "disabled",
    "checked",
    "readonly",
    "required",
    "selected",
    "multiple",
    "autofocus",
    "autoplay",
    "controls",
    "default",
    "defer",
    "formnovalidate",
    "hidden",
    "ismap",
    "loop",
    "nomodule",
    "novalidate",
    "open",
    "reversed",
    "scoped",
    "seamless"
  )

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
