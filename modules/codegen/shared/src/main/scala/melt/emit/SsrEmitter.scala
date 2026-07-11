/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.emit

import melt.codegen.{ HtmlVoidElements, LineTracker }
import melt.ir.*

/** Emits JVM-targeted HTML string-rendering code from an [[IrComponent]].
  *
  * Replaces [[melt.codegen.SsrCodeGen.generate]] with a type-safe dispatch
  * over [[IrNode]] / [[IrAttr]] variants.
  *
  * Generated code is semantically identical to [[melt.codegen.SsrCodeGen.generate]] output;
  * Phase 3 wires `SsrCodeGen.generate()` to call `AstToIr.lower + SsrEmitter.emit`.
  */
object SsrEmitter:

  /** Emits a complete `.scala` source file from a lowered [[IrComponent]]. */
  def emit(ir: IrComponent): String =
    val tracker = new LineTracker

    if ir.pkg.nonEmpty then tracker ++= s"package ${ ir.pkg }\n\n"
    tracker ++= "import scala.language.implicitConversions\n"
    tracker ++= "import melt.runtime.*\n"
    if ir.propsType.exists(!_.isNamedTuple) then tracker ++= "import melt.runtime.json.PropsCodec\n"
    tracker ++= "import melt.runtime.render.*\n\n"

    tracker ++= s"object ${ ir.objectName } {\n\n"
    tracker ++= s"""  private val _scopeId = "${ ir.scopeId }"\n\n"""

    val hasCss = ir.style.isDefined
    ir.style.foreach { s =>
      tracker ++= s"""  private val _css =\n    "${ escapeStr(s.scopedCss) }"\n\n"""
    }

    // ── module script (shared across all instances) ────────────────────────
    if ir.moduleBody.nonEmpty then
      tracker ++= "  // ── module script ──\n"
      ir.moduleBody.linesIterator.foreach(line => tracker ++= s"  $line\n")
      tracker += '\n'

    ir.typeDecls.foreach { decl =>
      decl.linesIterator.foreach(line => tracker ++= s"  $line\n")
      tracker += '\n'
    }

    // ── Named Tuple Props: emit object Props factory ───────────────────────
    ir.propsType.foreach { pt =>
      if pt.isNamedTuple && pt.namedTupleFields.nonEmpty then emitNamedTuplePropsFactory(pt, tracker, ir.objectName)
    }

    // ── Props alias when baseName != "Props" (non-Named-Tuple only) ───────
    ir.propsType.foreach { pt =>
      if pt.baseName != "Props" && !pt.isNamedTuple then
        tracker ++= s"  val Props = ${ pt.baseName }\n"
        if pt.typeParams.nonEmpty then tracker ++= s"  type Props${ pt.typeParams } = ${ pt.typeName }\n"
        else tracker ++= s"  type Props = ${ pt.baseName }\n"
        tracker += '\n'
    }

    // ── PropsCodec (Named Tuple not supported until Scala 3.7+) ──────────
    ir.propsType.foreach { pt =>
      if pt.typeParams.isEmpty && !pt.isNamedTuple then
        tracker ++= s"  private val _propsCodec: PropsCodec[${ pt.typeName }] = PropsCodec.derived\n\n"
    }

    val hasChildren = templateHasChildrenRef(ir.template)
    val _tp         = ir.propsType.map(_.typeParams).getOrElse("")
    tracker ++= s"  def apply$_tp(${ renderParams(ir.propsType, hasChildren) }): RenderResult = {\n"
    tracker ++= "    val renderer = ServerRenderer()\n"
    val moduleId = kebabCase(ir.objectName)
    tracker ++= s"""    renderer.trackComponent("$moduleId")\n"""
    ir.propsType.foreach { pt =>
      if pt.typeParams.isEmpty && !pt.isNamedTuple then
        tracker ++= s"""    renderer.trackHydrationProps("$moduleId", _propsCodec.encodeToString(props))\n"""
    }
    if hasCss then tracker ++= "    renderer.css.add(_scopeId, _css)\n"
    ir.fileImports.foreach { path =>
      tracker ++= s"""    renderer.addImport("${ escapeStr(path) }")\n"""
    }
    tracker ++= "\n"

    if ir.scriptBody.trim.nonEmpty then
      tracker ++= "    // ── User script section ──\n"
      tracker.markSourceLine(ir.scriptBodyLine)
      ir.scriptBody.linesIterator.foreach { line =>
        if line.trim.isEmpty then tracker ++= "\n"
        else tracker ++= s"    $line\n"
      }
      tracker ++= "\n"

    tracker ++= s"""    renderer.push(HydrationMarkers.open("$moduleId"))\n"""
    tracker ++= "    // ── Template ──\n"

    val nodePos    = ir.nodePositions
    val hoistedMap = ir.hoistedNodes.map(h => h.id -> h.node).toMap
    ir.template.foreach(node => emitNode(node, tracker, indent = 4, ir.scopeId, nodePos, hoistedMap))

    tracker ++= s"""    renderer.push(HydrationMarkers.close("$moduleId"))\n"""
    tracker ++= "\n    renderer.result()\n"
    tracker ++= "  }\n"
    tracker ++= "}\n"

    val entries        = tracker.mappings()
    val safeSourcePath = ir.sourcePath.replace("\n", "").replace("\r", "").replace("*/", "*\\/").replace("/*", "/\\*")
    val meta           =
      if ir.sourcePath.nonEmpty && entries.nonEmpty then
        val v3 = melt.codegen.SourceMapV3.generateBase64(ir.sourcePath, entries)
        s"\n/*\n    -- MELT GENERATED --\n    SOURCE: $safeSourcePath\n    V3: $v3\n    -- MELT GENERATED --\n*/\n"
      else ""

    tracker.result() + meta

  // ── Node emission ──────────────────────────────────────────────────────────

  private def emitNode(
    node:           IrNode,
    buf:            LineTracker,
    indent:         Int,
    scopeId:        String,
    nodePos:        IrNodePositions = IrNodePositions.empty,
    hoistedMap:     Map[String, IrNode.IrStaticElement] = Map.empty,
    selectBindExpr: Option[String] = None
  ): Unit =
    nodePos.get(node).foreach { case (l, c) => buf.markSourceLine(l, c) }
    val pad = " " * indent

    node match
      case IrNode.IrStaticText(content) =>
        val escaped = htmlEscapeLiteral(content)
        buf ++= s"""${ pad }renderer.push("${ escapeStr(escaped) }")\n"""

      case IrNode.IrDynamicText(expr, _, _) =>
        buf ++= s"""${ pad }renderer.push(Escape.html(${ expr.code }))\n"""

      case IrNode.IrStaticElement(tag, _, attrs, children, _) =>
        emitElementSSR(tag, attrs, children, buf, indent, scopeId, nodePos, hoistedMap, selectBindExpr)

      case IrNode.IrElement(tag, _, attrs, children, _) =>
        val hasBindInnerHtml   = attrs.exists { case IrAttr.BindInnerHtml(_) => true; case _ => false }
        val hasBindTextContent = attrs.exists { case IrAttr.BindTextContent(_) => true; case _ => false }

        attrs.collectFirst { case IrAttr.BindTextareaValue(expr) => expr.code } match
          case Some(bindExpr) =>
            emitTextareaBindValue(bindExpr, attrs, children, buf, indent, scopeId, nodePos, hoistedMap)
          case None =>
            attrs.collectFirst { case IrAttr.BindSelectValue(expr, _) => expr.code } match
              case Some(bindExpr) =>
                emitSelectBindValue(tag, bindExpr, attrs, children, buf, indent, scopeId, nodePos, hoistedMap)
              case None =>
                attrs.collectFirst { case IrAttr.BindGroup(expr, chk) => (expr.code, chk) } match
                  case Some((bindExpr, isCheckbox)) =>
                    emitInputBindGroup(tag, bindExpr, isCheckbox, attrs, buf, indent, scopeId)
                  case None =>
                    if hasBindInnerHtml || hasBindTextContent then
                      emitElementWithBindContent(tag, attrs, buf, indent, scopeId)
                    else emitElementSSR(tag, attrs, children, buf, indent, scopeId, nodePos, hoistedMap, selectBindExpr)

      case IrNode.IrComponent(name, props, childrenSlot, spreadExpr, _, _) =>
        emitComponentSSR(name, props, childrenSlot, spreadExpr, buf, indent, scopeId, nodePos, hoistedMap)

      case IrNode.IrChildren =>
        buf ++= s"${ pad }renderer.merge(children())\n"

      case IrNode.IrRawHtml(_, expr) =>
        buf ++= s"${ pad }renderer.push((${ expr.code }).value)\n"

      case IrNode.IrList(source, renderFn) =>
        val expr = s"${ source.code }.value.map(${ renderFn.code })"
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }$expr.foreach(renderer.push)\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""

      case IrNode.IrKeyedList(source, keyFn, renderFn) =>
        val expr = s"${ source.code }.keyed(${ keyFn.code }).map(${ renderFn.code })"
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }$expr.foreach(renderer.push)\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""

      case IrNode.IrConditional(_, condAndBody) =>
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }renderer.push(${ condAndBody.code })\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""

      case IrNode.IrDomResult(expr) =>
        buf ++= s"${ pad }renderer.push(${ expr.code })\n"

      case IrNode.IrFragmentResult(expr) =>
        buf ++= s"${ pad }renderer.push(${ expr.code })\n"

      case IrNode.IrHead(children) =>
        children.foreach(c => emitHeadNodeSSR(c, buf, indent, scopeId, nodePos, hoistedMap))

      case IrNode.IrWindow(_) | IrNode.IrBody(_) | IrNode.IrDocument(_) =>
        ()

      case IrNode.IrInlineTemplate(parts) =>
        emitInlineTemplateSSR(parts, buf, indent, scopeId, nodePos, hoistedMap)

      case IrNode.IrDynamicElement(_, _, _, _) =>
        buf ++= s"${ pad }// TODO(SSR Phase C): DynamicElement\n"

      case IrNode.IrBoundary(children, _, _, _) =>
        children.foreach(c => emitNode(c, buf, indent, scopeId, nodePos, hoistedMap))

      case IrNode.IrKeyBlock(_, children) =>
        children.foreach(c => emitNode(c, buf, indent, scopeId, nodePos, hoistedMap))

      case IrNode.IrSnippetDef(_, _, _) =>
        ()

      case IrNode.IrRenderCall(expr) =>
        buf ++= s"${ pad }renderer.push(${ expr.code })\n"

      case IrNode.IrHoistRef(id, _) =>
        hoistedMap.get(id) match
          case Some(elem) =>
            emitElementSSR(
              elem.tag,
              elem.attrs,
              elem.children,
              buf,
              indent,
              scopeId,
              nodePos,
              hoistedMap,
              selectBindExpr
            )
          case None => ()

  // ── Generic element emission ───────────────────────────────────────────────

  private def emitElementSSR(
    tag:            String,
    attrs:          List[IrAttr],
    children:       List[IrNode],
    buf:            LineTracker,
    indent:         Int,
    scopeId:        String,
    nodePos:        IrNodePositions,
    hoistedMap:     Map[String, IrNode.IrStaticElement],
    selectBindExpr: Option[String]
  ): Unit =
    val pad = " " * indent

    emitElementStart(tag, attrs, buf, indent, scopeId)

    if tag.equalsIgnoreCase("option") then
      for
        bindExpr  <- selectBindExpr
        valueExpr <- optionValueExprIR(attrs)
      do buf ++= s"${ pad }if ($bindExpr == $valueExpr) renderer.push(\" selected\")\n"

    if HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }renderer.push(">")\n"""
    else
      buf ++= s"""${ pad }renderer.push(">")\n"""
      children.foreach(c => emitNode(c, buf, indent, scopeId, nodePos, hoistedMap, selectBindExpr))
      buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

  private def emitElementStart(
    tag:     String,
    attrs:   List[IrAttr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent
    buf ++= s"""${ pad }renderer.push("<$tag")\n"""
    emitScopedClassAttr(tag, attrs, buf, indent, scopeId)
    attrs.foreach(attr => emitAttrSSR(tag, attr, buf, indent))

  /** Emits the `class="scopeId"` attribute, optionally augmented by
    * [[IrAttr.ClassToggle]] and [[IrAttr.DynamicClass]] bindings.
    */
  private def emitScopedClassAttr(
    tag:     String,
    attrs:   List[IrAttr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad          = " " * indent
    val staticClass  = attrs.collectFirst { case IrAttr.StaticAttr("class", v) => v }
    val classToggles = attrs.collect { case IrAttr.ClassToggle(name, expr) => (name, expr.code) }
    val dynamicClass = attrs.collectFirst { case IrAttr.DynamicClass(expr) => expr.code }
    val baseClass    = staticClass.map(cls => s"$cls $scopeId").getOrElse(scopeId)

    val condParts = classToggles.map { case (name, expr) => s"""(if ($expr) " $name" else "")""" }
    val dynPart   = dynamicClass.map(d => s""" " " + Escape.attr($d)""")

    if condParts.isEmpty && dynPart.isEmpty then
      val combined = escapeStr(s""" class="$baseClass"""")
      buf ++= s"""${ pad }renderer.push("$combined")\n"""
    else
      val allParts = (dynPart.toList ++ condParts).mkString(" + ")
      buf ++= s"""${ pad }renderer.push(" class=\\"${ escapeStr(baseClass) }" + $allParts + "\\"")\n"""

  /** Emits one non-`class`-static attribute. */
  private def emitAttrSSR(
    tag:    String,
    attr:   IrAttr,
    buf:    LineTracker,
    indent: Int
  ): Unit =
    val pad = " " * indent
    attr match
      case IrAttr.StaticAttr("class", _) => ()
      case IrAttr.DynamicClass(_)        => () // handled in emitScopedClassAttr
      case IrAttr.ClassToggle(_, _)      => () // handled in emitScopedClassAttr

      case IrAttr.StaticAttr(name, value) =>
        val combined = escapeStr(s""" $name="${ htmlAttrEscapeLiteral(value) }"""")
        buf ++= s"""${ pad }renderer.push("$combined")\n"""

      case IrAttr.BooleanAttr(name) =>
        val lit = escapeStr(s" $name")
        buf ++= s"""${ pad }renderer.push("$lit")\n"""

      case IrAttr.DynamicBooleanAttr(name, expr) =>
        buf ++= s"""${ pad }renderer.push(s" $name=\\"" + Escape.attr(${ expr.code }) + "\\"")\n"""

      case IrAttr.DynamicAttr(name, expr) =>
        if isUrlAttr(tag, name) then
          buf ++= s"""${ pad }renderer.push(s" $name=\\"" + Escape.url(${ expr.code }) + "\\"")\n"""
        else buf ++= s"""${ pad }renderer.push(s" $name=\\"" + Escape.attr(${ expr.code }) + "\\"")\n"""

      case IrAttr.Spread(expr) =>
        buf ++= s"""${ pad }renderer.spreadAttrs("$tag", ${ expr.code })\n"""

      case IrAttr.BindInputValue(expr) =>
        buf ++= s"""${ pad }renderer.push(s" value=\\"" + Escape.attr(${ expr.code }) + "\\"")\n"""

      case IrAttr.BindInputValueInt(expr) =>
        buf ++= s"""${ pad }renderer.push(s" value=\\"" + ${ expr.code }.toString + "\\"")\n"""

      case IrAttr.BindInputValueDouble(expr) =>
        buf ++= s"""${ pad }renderer.push(s" value=\\"" + ${ expr.code }.toString + "\\"")\n"""

      case IrAttr.BindChecked(expr) =>
        buf ++= s"""${ pad }renderer.push(s" checked=\\"" + Escape.attr(${ expr.code }) + "\\"")\n"""

      case IrAttr.StyleProp(prop, expr) =>
        buf ++= s"""${ pad }renderer.push(s" style=\\"$prop:" + Escape.cssValue(${ expr.code }) + ";\\"")\n"""

      // Handled at element level — not emitted as individual attributes
      case IrAttr.BindTextareaValue(_)  => ()
      case IrAttr.BindSelectValue(_, _) => ()
      case IrAttr.BindGroup(_, _)       => ()
      case IrAttr.BindInnerHtml(_)      => ()
      case IrAttr.BindTextContent(_)    => ()

      // No SSR meaning
      case IrAttr.BindThis(_)                       => ()
      case IrAttr.BindDimension(_, _)               => ()
      case IrAttr.BindMedia(_, _)                   => ()
      case IrAttr.BindWindow(_, _)                  => ()
      case IrAttr.BindDocument(_, _)                => ()
      case IrAttr.UseAction(_, _)                   => ()
      case IrAttr.Transition(_, _, _, _)            => ()
      case IrAttr.Animate(_, _)                     => ()
      case IrAttr.EventHandler(_, _)                => ()
      case IrAttr.EventHandlerWithModifier(_, _, _) => ()

  // ── Special element handlers ───────────────────────────────────────────────

  /** `<textarea bind:value={v}>` — value becomes element body content. */
  private def emitTextareaBindValue(
    bindExpr:   String,
    attrs:      List[IrAttr],
    children:   List[IrNode],
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodePos:    IrNodePositions,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    val pad       = " " * indent
    val restAttrs = attrs.filterNot { case IrAttr.BindTextareaValue(_) => true; case _ => false }

    emitElementStart("textarea", restAttrs, buf, indent, scopeId)
    buf ++= s"""${ pad }renderer.push(">")\n"""
    children.foreach(c => emitNode(c, buf, indent, scopeId, nodePos, hoistedMap))
    buf ++= s"${ pad }renderer.push(Escape.html($bindExpr))\n"
    buf ++= s"""${ pad }renderer.push("</textarea>")\n"""

  /** `<select bind:value={v}>` — passes bind expression to option children as `selectBindExpr`. */
  private def emitSelectBindValue(
    tag:        String,
    bindExpr:   String,
    attrs:      List[IrAttr],
    children:   List[IrNode],
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodePos:    IrNodePositions,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    val pad       = " " * indent
    val restAttrs = attrs.filterNot { case IrAttr.BindSelectValue(_, _) => true; case _ => false }

    emitElementStart(tag, restAttrs, buf, indent, scopeId)
    buf ++= s"""${ pad }renderer.push(">")\n"""
    children.foreach(c => emitNode(c, buf, indent, scopeId, nodePos, hoistedMap, selectBindExpr = Some(bindExpr)))
    buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

  /** `<input bind:group={arr}>` — emits `checked` conditionally based on radio/checkbox type. */
  private def emitInputBindGroup(
    tag:        String,
    bindExpr:   String,
    isCheckbox: Boolean,
    attrs:      List[IrAttr],
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String
  ): Unit =
    val pad       = " " * indent
    val valueExpr = attrs.collectFirst {
      case IrAttr.StaticAttr("value", v)  => s""""${ escapeStr(v) }""""
      case IrAttr.DynamicAttr("value", e) => e.code
    }

    val restAttrs = attrs.filterNot { case IrAttr.BindGroup(_, _) => true; case _ => false }

    emitElementStart(tag, restAttrs, buf, indent, scopeId)

    (isCheckbox, valueExpr) match
      case (true, Some(v)) =>
        buf ++= s"${ pad }if ($bindExpr.contains($v)) renderer.push(\" checked\")\n"
      case (_, Some(v)) =>
        buf ++= s"${ pad }if ($bindExpr == $v) renderer.push(\" checked\")\n"
      case _ => ()

    buf ++= s"""${ pad }renderer.push(">")\n"""

  /** Any element with `bind:innerHTML={v}` or `bind:textContent={v}`. */
  private def emitElementWithBindContent(
    tag:     String,
    attrs:   List[IrAttr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent

    val innerHtmlExpr   = attrs.collectFirst { case IrAttr.BindInnerHtml(expr) => expr.code }
    val textContentExpr =
      if innerHtmlExpr.isDefined then None
      else attrs.collectFirst { case IrAttr.BindTextContent(expr) => expr.code }

    val restAttrs = attrs.filterNot {
      case IrAttr.BindInnerHtml(_)   => true
      case IrAttr.BindTextContent(_) => true
      case _                         => false
    }

    emitElementStart(tag, restAttrs, buf, indent, scopeId)
    if HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }renderer.push(">")\n"""
    else
      buf ++= s"""${ pad }renderer.push(">")\n"""
      innerHtmlExpr match
        case Some(e) => buf ++= s"${ pad }renderer.push($e.value)\n"
        case None    => textContentExpr.foreach { e => buf ++= s"${ pad }renderer.push(Escape.html($e))\n" }
      buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

  // ── Component emission ─────────────────────────────────────────────────────

  private def emitComponentSSR(
    name:         String,
    props:        List[IrProp],
    childrenSlot: Option[IrChildrenSlot],
    spreadExpr:   Option[ScalaExpr],
    buf:          LineTracker,
    indent:       Int,
    scopeId:      String,
    nodePos:      IrNodePositions,
    hoistedMap:   Map[String, IrNode.IrStaticElement]
  ): Unit =
    val pad  = " " * indent
    val args = props.map(p => s"${ p.name } = ${ emitPropValue(p.value) }")

    val propsStr = spreadExpr match
      case Some(e) => e.code
      case None    => if args.isEmpty then "" else s"$name.Props(${ args.mkString(", ") })"

    childrenSlot match
      case None =>
        if propsStr.isEmpty then buf ++= s"${ pad }renderer.merge($name())\n"
        else buf ++= s"${ pad }renderer.merge($name($propsStr))\n"

      case Some(slot) =>
        if propsStr.isEmpty then
          buf ++= s"${ pad }renderer.merge($name(children = () => {\n"
          buf ++= s"${ pad }  val _sb = new StringBuilder\n"
          slot.nodes.foreach(c => emitNodeToSb(c, buf, s"$pad  ", scopeId, nodePos, hoistedMap))
          buf ++= s"${ pad }  RenderResult(_sb.toString, \"\")\n"
          buf ++= s"${ pad }}))\n"
        else
          buf ++= s"${ pad }renderer.merge($name($propsStr, children = () => {\n"
          buf ++= s"${ pad }  val _sb = new StringBuilder\n"
          slot.nodes.foreach(c => emitNodeToSb(c, buf, s"$pad  ", scopeId, nodePos, hoistedMap))
          buf ++= s"${ pad }  RenderResult(_sb.toString, \"\")\n"
          buf ++= s"${ pad }}))\n"

  // ── InlineTemplate emission ────────────────────────────────────────────────

  private def emitInlineTemplateSSR(
    parts:      List[IrInlineTemplatePart],
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodePos:    IrNodePositions,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    val pad = " " * indent

    val exprBuf = new StringBuilder
    parts.foreach {
      case IrInlineTemplatePart.Code(code)  => exprBuf ++= code
      case IrInlineTemplatePart.Html(nodes) =>
        exprBuf ++= irNodesToStringExpr(nodes, scopeId, hoistedMap)
    }

    val expr = exprBuf.toString
    val kind = classifyInlineKind(parts)

    kind match
      case InlineKind.Iterable =>
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }$expr.foreach(renderer.push)\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""
      case InlineKind.SingleString =>
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }renderer.push($expr)\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""

  // ── Head emission ──────────────────────────────────────────────────────────

  private def emitHeadNodeSSR(
    node:       IrNode,
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodePos:    IrNodePositions,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    val pad = " " * indent
    node match
      // title with single dynamic expression → renderer.head.title(expr)
      case IrNode.IrStaticElement("title", _, _, titleChildren, _) =>
        emitHeadTitleNode(titleChildren, buf, pad, indent, scopeId, nodePos, hoistedMap)
      case IrNode.IrElement("title", _, _, titleChildren, _) =>
        emitHeadTitleNode(titleChildren, buf, pad, indent, scopeId, nodePos, hoistedMap)

      case IrNode.IrStaticElement(tag, _, attrs, children, _) =>
        emitHeadElementNode(tag, attrs, children, buf, pad, indent, scopeId, nodePos, hoistedMap)
      case IrNode.IrElement(tag, _, attrs, children, _) =>
        emitHeadElementNode(tag, attrs, children, buf, pad, indent, scopeId, nodePos, hoistedMap)

      case IrNode.IrStaticText(content) =>
        buf ++= s"""${ pad }renderer.head.push("${ escapeStr(htmlEscapeLiteral(content)) }")\n"""

      case IrNode.IrDynamicText(expr, _, _) =>
        buf ++= s"${ pad }renderer.head.push(Escape.html(${ expr.code }))\n"

      case _ => ()

  private def emitHeadTitleNode(
    titleChildren: List[IrNode],
    buf:           LineTracker,
    pad:           String,
    indent:        Int,
    scopeId:       String,
    nodePos:       IrNodePositions,
    hoistedMap:    Map[String, IrNode.IrStaticElement]
  ): Unit =
    titleChildren match
      case IrNode.IrDynamicText(expr, _, _) :: Nil =>
        buf ++= s"${ pad }renderer.head.title(${ expr.code })\n"
      case _ =>
        val staticText = titleChildren.collect { case IrNode.IrStaticText(t) => t }.mkString
        buf ++= s"""${ pad }renderer.head.push("<title>${ escapeStr(htmlEscapeLiteral(staticText)) }</title>")\n"""

  private def emitHeadElementNode(
    tag:        String,
    attrs:      List[IrAttr],
    children:   List[IrNode],
    buf:        LineTracker,
    pad:        String,
    indent:     Int,
    scopeId:    String,
    nodePos:    IrNodePositions,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    buf ++= s"""${ pad }renderer.head.push("<$tag")\n"""
    attrs.foreach {
      case IrAttr.StaticAttr(n, v) =>
        val lit = escapeStr(s""" $n="${ htmlAttrEscapeLiteral(v) }"""")
        buf ++= s"""${ pad }renderer.head.push("$lit")\n"""
      case IrAttr.DynamicAttr(n, e) =>
        buf ++= s"""${ pad }renderer.head.push(s" $n=\\"" + Escape.attr(${ e.code }) + "\\"")\n"""
      case _ => ()
    }
    if HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }renderer.head.push(">")\n"""
    else
      buf ++= s"""${ pad }renderer.head.push(">")\n"""
      children.foreach(c => emitHeadNodeSSR(c, buf, indent, scopeId, nodePos, hoistedMap))
      buf ++= s"""${ pad }renderer.head.push("</$tag>")\n"""

  // ── StringBuilder helpers (for component children and InlineTemplate) ──────

  /** Converts IR nodes into a `{ val _sb = new StringBuilder; ...; _sb.toString }` expression. */
  private def irNodesToStringExpr(
    nodes:      List[IrNode],
    scopeId:    String,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): String =
    val sb = new StringBuilder
    sb ++= "{ val _sb = new StringBuilder; "
    nodes.foreach(n => appendIrNodeToSb(n, sb, scopeId, hoistedMap))
    sb ++= "_sb.toString }"
    sb.toString

  /** Emits a single IR node as `_sb ++=` statements into `buf`, one node per line,
    * with [[LineTracker.markSourceLine]] calls for source-map tracking.
    *
    * Used inside component children lambdas emitted by [[emitComponentSSR]].
    */
  private def emitNodeToSb(
    node:       IrNode,
    buf:        LineTracker,
    pad:        String,
    scopeId:    String,
    nodePos:    IrNodePositions,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    nodePos.get(node).foreach { case (l, c) => buf.markSourceLine(l, c) }
    node match
      case IrNode.IrStaticText(content) =>
        val escaped = escapeStr(htmlEscapeLiteral(content))
        if escaped.nonEmpty then buf ++= s"""${ pad }_sb ++= "$escaped"\n"""

      case IrNode.IrDynamicText(expr, _, _) =>
        buf ++= s"${ pad }_sb ++= Escape.html(${ expr.code })\n"

      case IrNode.IrChildren =>
        buf ++= s"${ pad }{ val _r = children(); renderer.mergeMeta(_r); _sb ++= _r.body }\n"

      case IrNode.IrRawHtml(_, expr) =>
        buf ++= s"${ pad }_sb ++= (${ expr.code }).value\n"

      case IrNode.IrStaticElement(tag, _, attrs, children, _) =>
        val stmts = buildElementSbStmts(tag, attrs, scopeId)
        buf ++= s"$pad${ stmts.mkString("; ") }\n"
        children.foreach(c => emitNodeToSb(c, buf, pad, scopeId, nodePos, hoistedMap))
        if !HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }_sb ++= "</$tag>"\n"""

      case IrNode.IrElement(tag, _, attrs, children, _) =>
        val stmts = buildElementSbStmts(tag, attrs, scopeId)
        buf ++= s"$pad${ stmts.mkString("; ") }\n"
        children.foreach(c => emitNodeToSb(c, buf, pad, scopeId, nodePos, hoistedMap))
        if !HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }_sb ++= "</$tag>"\n"""

      case _ =>
        // Fall back to inline style for Component, InlineTemplate, etc.
        val nodeSb = new StringBuilder
        appendIrNodeToSb(node, nodeSb, scopeId, hoistedMap)
        val inline = nodeSb.toString
        if inline.nonEmpty then buf ++= s"$pad${ inline.stripSuffix("; ") }\n"

  /** Appends inline `_sb ++=` code for a single IR node into `sb` (no line breaks). */
  private def appendIrNodeToSb(
    node:       IrNode,
    sb:         StringBuilder,
    scopeId:    String,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit = node match
    case IrNode.IrStaticText(content) =>
      val escaped = escapeStr(htmlEscapeLiteral(content))
      sb ++= s"""_sb ++= "$escaped"; """

    case IrNode.IrDynamicText(expr, _, _) =>
      sb ++= s"""_sb ++= Escape.html(${ expr.code }); """

    case IrNode.IrChildren =>
      sb ++= """{ val _r = children(); renderer.mergeMeta(_r); _sb ++= _r.body }; """

    case IrNode.IrRawHtml(_, expr) =>
      sb ++= s"""_sb ++= (${ expr.code }).value; """

    case IrNode.IrStaticElement(tag, _, attrs, children, _) =>
      appendIrElementToSb(tag, attrs, children, sb, scopeId, hoistedMap)

    case IrNode.IrElement(tag, _, attrs, children, _) =>
      appendIrElementToSb(tag, attrs, children, sb, scopeId, hoistedMap)

    case IrNode.IrComponent(name, props, childrenSlot, spreadExpr, _, _) =>
      val args = props.map(p => s"${ p.name } = ${ emitPropValue(p.value) }")
      val childrenArg: Option[String] = childrenSlot.map { slot =>
        s"children = ${ buildSsrChildrenLambdaInline(slot.nodes, scopeId, hoistedMap) }"
      }
      val propsStr = spreadExpr match
        case Some(e) => e.code
        case None    => if args.isEmpty then "" else s"$name.Props(${ args.mkString(", ") })"
      val call = (propsStr, childrenArg) match
        case ("", None)    => s"$name()"
        case (p, None)     => s"$name($p)"
        case ("", Some(c)) => s"$name($c)"
        case (p, Some(c))  => s"$name($p, $c)"
      sb ++= s"""{ val _r = $call; renderer.mergeMeta(_r); _sb ++= _r.body }; """

    case IrNode.IrInlineTemplate(parts) =>
      val exprBuf = new StringBuilder
      parts.foreach {
        case IrInlineTemplatePart.Code(code)  => exprBuf ++= code
        case IrInlineTemplatePart.Html(nodes) => exprBuf ++= irNodesToStringExpr(nodes, scopeId, hoistedMap)
      }
      val expr = exprBuf.toString
      val kind = classifyInlineKind(parts)
      sb ++= """_sb ++= "<!--[melt:dyn-->"; """
      kind match
        case InlineKind.Iterable     => sb ++= s"$expr.foreach(s => _sb ++= s); "
        case InlineKind.SingleString => sb ++= s"_sb ++= ($expr); "
      sb ++= """_sb ++= "<!--]melt:dyn-->"; """

    case IrNode.IrHoistRef(id, _) =>
      hoistedMap.get(id) match
        case Some(elem) => appendIrElementToSb(elem.tag, elem.attrs, elem.children, sb, scopeId, hoistedMap)
        case None       => ()

    case _ => ()

  private def appendIrElementToSb(
    tag:        String,
    attrs:      List[IrAttr],
    children:   List[IrNode],
    sb:         StringBuilder,
    scopeId:    String,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): Unit =
    val stmts = buildElementSbStmts(tag, attrs, scopeId)
    stmts.foreach(s => sb ++= s"$s; ")
    if !HtmlVoidElements.isVoid(tag) then
      children.foreach(c => appendIrNodeToSb(c, sb, scopeId, hoistedMap))
      sb ++= s"""_sb ++= "</$tag>"; """

  /** Builds `_sb ++=` statement strings for a single element's opening tag including `>`. */
  private def buildElementSbStmts(
    tag:     String,
    attrs:   List[IrAttr],
    scopeId: String
  ): List[String] =
    val stmts        = scala.collection.mutable.ListBuffer.empty[String]
    val staticClass  = attrs.collectFirst { case IrAttr.StaticAttr("class", v) => v }
    val classToggles = attrs.collect { case IrAttr.ClassToggle(name, expr) => (name, expr.code) }
    val dynamicClass = attrs.collectFirst { case IrAttr.DynamicClass(expr) => expr.code }
    val baseClass    = staticClass.map(cls => s"$cls $scopeId").getOrElse(scopeId)

    stmts += s"""_sb ++= "<$tag""""

    val condParts = classToggles.map { case (name, expr) => s"""(if ($expr) " $name" else "")""" }
    val dynPart   = dynamicClass.map(d => s""" " " + Escape.attr($d)""")
    if condParts.isEmpty && dynPart.isEmpty then stmts += s"""_sb ++= " class=\\"${ escapeStr(baseClass) }\\"""""
    else
      val allParts = (dynPart.toList ++ condParts).mkString(" + ")
      stmts += s"""_sb ++= " class=\\"${ escapeStr(baseClass) }" + $allParts + "\\"""""

    attrs.foreach {
      case IrAttr.StaticAttr("class", _)  => ()
      case IrAttr.DynamicClass(_)         => ()
      case IrAttr.ClassToggle(_, _)       => ()
      case IrAttr.StaticAttr(name, value) =>
        val lit = escapeStr(s""" $name="${ htmlAttrEscapeLiteral(value) }"""")
        stmts += s"""_sb ++= "$lit""""
      case IrAttr.BooleanAttr(name) =>
        val lit = escapeStr(s" $name")
        stmts += s"""_sb ++= "$lit""""
      case IrAttr.DynamicBooleanAttr(name, expr) =>
        stmts += s"""_sb ++= s" $name=\\"" + Escape.attr(${ expr.code }) + "\\"""""
      case IrAttr.DynamicAttr(name, expr) =>
        if isUrlAttr(tag, name) then stmts += s"""_sb ++= s" $name=\\"" + Escape.url(${ expr.code }) + "\\"""""
        else stmts += s"""_sb ++= s" $name=\\"" + Escape.attr(${ expr.code }) + "\\"""""
      case IrAttr.BindInputValue(expr) =>
        stmts += s"""_sb ++= s" value=\\"" + Escape.attr(${ expr.code }) + "\\"""""
      case IrAttr.BindInputValueInt(expr) =>
        stmts += s"""_sb ++= s" value=\\"" + ${ expr.code }.toString + "\\"""""
      case IrAttr.BindInputValueDouble(expr) =>
        stmts += s"""_sb ++= s" value=\\"" + ${ expr.code }.toString + "\\"""""
      case IrAttr.BindChecked(expr) =>
        stmts += s"""_sb ++= s" checked=\\"" + Escape.attr(${ expr.code }) + "\\"""""
      case IrAttr.StyleProp(prop, expr) =>
        stmts += s"""_sb ++= s" style=\\"$prop:" + Escape.cssValue(${ expr.code }) + ";\\"""""
      case _ => ()
    }

    stmts += s"""_sb ++= ">""""
    stmts.toList

  /** Builds a `() => RenderResult` lambda expression string (for inline use). */
  private def buildSsrChildrenLambdaInline(
    nodes:      List[IrNode],
    scopeId:    String,
    hoistedMap: Map[String, IrNode.IrStaticElement]
  ): String =
    val sb = new StringBuilder
    sb ++= "() => { val _sb = new StringBuilder; "
    nodes.foreach(n => appendIrNodeToSb(n, sb, scopeId, hoistedMap))
    sb ++= "RenderResult(_sb.toString, \"\") }"
    sb.toString

  // ── Inline template classification ─────────────────────────────────────────

  private enum InlineKind:
    case Iterable
    case SingleString

  private def classifyInlineKind(parts: List[IrInlineTemplatePart]): InlineKind =
    val codeOnly = parts.collect { case IrInlineTemplatePart.Code(c) => c }.mkString
    val trimmed  = codeOnly.trim
    if trimmed.contains(".map(") || trimmed.contains(".keyed(") then InlineKind.Iterable
    else InlineKind.SingleString

  // ── Children detection ─────────────────────────────────────────────────────

  private def templateHasChildrenRef(nodes: List[IrNode]): Boolean =
    nodes.exists {
      case IrNode.IrChildren                     => true
      case IrNode.IrElement(_, _, _, c, _)       => templateHasChildrenRef(c)
      case IrNode.IrStaticElement(_, _, _, c, _) => templateHasChildrenRef(c)
      case IrNode.IrHead(c)                      => templateHasChildrenRef(c)
      case IrNode.IrBoundary(c, p, f, _)         =>
        templateHasChildrenRef(c) ||
        p.exists(templateHasChildrenRef) ||
        f.exists(fb => templateHasChildrenRef(fb.children))
      case IrNode.IrKeyBlock(_, c)      => templateHasChildrenRef(c)
      case IrNode.IrSnippetDef(_, _, c) => templateHasChildrenRef(c)
      // `{children}` may be forwarded into a nested component's slot
      // (e.g. `<Layout>{children}</Layout>`), a dynamic element, or an inline
      // template fragment — recurse into all of them so the `children`
      // parameter is generated consistently with the emitter.
      case IrNode.IrComponent(_, _, cs, _, _, _) => cs.exists(slot => templateHasChildrenRef(slot.nodes))
      case IrNode.IrDynamicElement(_, _, c, _)   => templateHasChildrenRef(c)
      case IrNode.IrInlineTemplate(parts)        =>
        parts.exists {
          case IrInlineTemplatePart.Html(ns) => templateHasChildrenRef(ns)
          case _                             => false
        }
      case _ => false
    }

  // ── Props helpers ──────────────────────────────────────────────────────────

  // ── Named Tuple Props factory ──────────────────────────────────────────────

  /** Emits an `object Props { def apply(...): ComponentName.Props = (...) }` factory
    * for Named Tuple Props so that call sites can use `ComponentName.Props(field = value)`.
    */
  private def emitNamedTuplePropsFactory(pt: IrPropsType, tracker: LineTracker, objectName: String): Unit =
    val paramList = pt.namedTupleFields.map { case (name, tpe) => s"$name: $tpe" }.mkString(", ")
    val argList   = pt.namedTupleFields.map { case (name, _) => s"$name = $name" }.mkString(", ")
    val retType   = s"$objectName.Props${ pt.typeParams }"
    tracker ++= s"  object Props:\n"
    tracker ++= s"    def apply${ pt.typeParams }($paramList): $retType =\n"
    tracker ++= s"      ($argList)\n"
    tracker += '\n'

  private def renderParams(propsType: Option[IrPropsType], hasChildren: Boolean): String =
    val propsPart = propsType match
      case Some(pt) if pt.typeParams.nonEmpty                 => s"props: ${ pt.typeName }"
      case Some(pt) if pt.allHaveDefaults && !pt.isNamedTuple => s"props: ${ pt.typeName } = ${ pt.typeName }()"
      case Some(pt)                                           => s"props: ${ pt.typeName }"
      case None                                               => ""
    val childrenPart =
      if hasChildren then "children: () => RenderResult = () => RenderResult.empty"
      else ""
    List(propsPart, childrenPart).filter(_.nonEmpty).mkString(", ")

  private def emitPropValue(v: IrPropValue): String = v match
    case IrPropValue.Static(value)   => s""""${ escapeStr(value) }""""
    case IrPropValue.Dynamic(expr)   => expr.code
    case IrPropValue.Shorthand(name) => name
    case IrPropValue.BooleanTrue     => "true"

  private def optionValueExprIR(attrs: List[IrAttr]): Option[String] =
    attrs.collectFirst {
      case IrAttr.StaticAttr("value", v)  => s""""${ escapeStr(v) }""""
      case IrAttr.DynamicAttr("value", e) => e.code
    }

  // ── String utilities ───────────────────────────────────────────────────────

  private def kebabCase(s: String): String =
    val buf = new StringBuilder(s.length + 4)
    s.zipWithIndex.foreach {
      case (c, i) =>
        if c.isUpper then
          if i > 0 then buf += '-'
          buf += c.toLower
        else buf += c
    }
    buf.toString

  private def escapeStr(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def htmlEscapeLiteral(s: String): String =
    val buf = new StringBuilder(s.length)
    s.foreach {
      case '&' => buf ++= "&amp;"
      case '<' => buf ++= "&lt;"
      case '>' => buf ++= "&gt;"
      case c   => buf += c
    }
    buf.toString

  private def htmlAttrEscapeLiteral(s: String): String =
    val buf = new StringBuilder(s.length)
    s.foreach {
      case '&' => buf ++= "&amp;"
      case '<' => buf ++= "&lt;"
      case '>' => buf ++= "&gt;"
      case '"' => buf ++= "&quot;"
      case c   => buf += c
    }
    buf.toString

  private def isUrlAttr(tag: String, attrName: String): Boolean =
    val t = tag.toLowerCase
    val a = attrName.toLowerCase
    globalUrlAttrs.contains(a) || specificUrlAttrs.contains((t, a))

  private val globalUrlAttrs: Set[String] = Set("xlink:href")

  private val specificUrlAttrs: Set[(String, String)] = Set(
    "a"          -> "href",
    "area"       -> "href",
    "base"       -> "href",
    "link"       -> "href",
    "img"        -> "src",
    "img"        -> "srcset",
    "source"     -> "src",
    "source"     -> "srcset",
    "track"      -> "src",
    "iframe"     -> "src",
    "script"     -> "src",
    "embed"      -> "src",
    "audio"      -> "src",
    "video"      -> "src",
    "video"      -> "poster",
    "input"      -> "src",
    "input"      -> "formaction",
    "button"     -> "formaction",
    "form"       -> "action",
    "object"     -> "data",
    "blockquote" -> "cite",
    "q"          -> "cite",
    "del"        -> "cite",
    "ins"        -> "cite"
  )
