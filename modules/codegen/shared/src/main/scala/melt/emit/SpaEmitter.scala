/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.emit

import scala.collection.mutable

import melt.codegen.{ Counter, LineTracker }
import melt.ir.*

/** Emits Scala.js DOM-manipulation code from an [[IrComponent]].
  *
  * Replaces the hand-written `emitNode` / `emitAttr` logic in [[melt.codegen.SpaCodeGen]]
  * with a type-safe dispatch over [[IrNode]] / [[IrAttr]] variants.
  *
  * The generated code is identical to [[melt.codegen.SpaCodeGen.generate]] output;
  * Phase 2 wires `SpaCodeGen.generate()` to call `AstToIr.lower + SpaEmitter.emit`.
  */
object SpaEmitter:

  private val SvgNs  = "http://www.w3.org/2000/svg"
  private val MathNs = "http://www.w3.org/1998/Math/MathML"

  /** Emits a complete `.scala` source file from a lowered [[IrComponent]]. */
  def emit(ir: IrComponent): String =
    val tracker = new LineTracker
    val ctr     = new Counter

    // ── package + imports ──────────────────────────────────────────────────
    if ir.pkg.nonEmpty then tracker ++= s"package ${ ir.pkg }\n\n"

    tracker ++= "import scala.language.implicitConversions\n"
    if ir.fileImports.nonEmpty then
      tracker ++= "import scala.scalajs.js\n"
      tracker ++= "import scala.scalajs.js.annotation.{ JSExportTopLevel, JSImport }\n"
    else tracker ++= "import scala.scalajs.js.annotation.JSExportTopLevel\n"
    tracker ++= "import org.scalajs.dom\n"
    tracker ++= "import melt.runtime.{ Bind, Cleanup, Mount, Ref, Style, State, Signal }\n"
    tracker ++= "import melt.runtime.*\n"
    if ir.hydration && ir.propsType.exists(!_.isNamedTuple) then
      tracker ++= "import melt.runtime.json.{ PropsCodec, SimpleJson }\n"
    tracker ++= "import melt.runtime.transition.*\n"
    tracker ++= "import melt.runtime.animate.*\n\n"

    // ── file-level JS imports ──────────────────────────────────────────────
    ir.fileImports.zipWithIndex.foreach { (path, idx) =>
      tracker ++= s"""@js.native @JSImport("${ escapeStr(path) }", JSImport.Namespace)\n"""
      tracker ++= s"private object _melt_import_$idx extends js.Object\n\n"
    }

    // ── object header ─────────────────────────────────────────────────────
    tracker ++= s"object ${ ir.objectName } {\n\n"
    tracker ++= s"""  private val _scopeId = "${ ir.scopeId }"\n\n"""

    ir.style.foreach { s =>
      val css = escapeStr(s.scopedCss)
      tracker ++= s"""  private val _css =\n    "$css"\n\n"""
    }

    // ── hoisted static elements ───────────────────────────────────────────
    if ir.hoistedNodes.nonEmpty then emitHoistedNodes(ir.hoistedNodes, tracker)

    // ── module script (shared across all instances) ────────────────────────
    if ir.moduleBody.nonEmpty then
      tracker ++= "  // ── module script ──\n"
      ir.moduleBody.linesIterator.foreach(line => tracker ++= s"  $line\n")
      tracker += '\n'

    // ── type declarations (Props case class etc.) ──────────────────────────
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

    // ── hydration PropsCodec (Named Tuple not supported until Scala 3.7+) ─
    if ir.hydration then
      ir.propsType.foreach { pt =>
        if pt.typeParams.isEmpty && !pt.isNamedTuple then
          tracker ++= s"  private val _propsCodec: PropsCodec[${ pt.typeName }] = PropsCodec.derived\n\n"
      }

    // ── apply() signature ─────────────────────────────────────────────────
    val hasChildren = templateHasChildrenRef(ir.template)
    // The field-forwarding overload is only emitted for non-children components:
    // a children component's props-based apply already carries a `children`
    // default, and Scala forbids two overloads both having default arguments.
    val emitFieldApply = !hasChildren && ir.propsType.exists(_.fieldForwardApplyFields.nonEmpty)
    ir.propsType match
      // When a field-forwarding overload is also emitted, the props-based apply
      // must NOT carry a default (Scala forbids two overloads both defaulting).
      case Some(pt) if pt.allHaveDefaults && !pt.isNamedTuple && pt.typeParams.isEmpty && !emitFieldApply =>
        if hasChildren then
          tracker ++= s"  def apply(props: ${ pt.typeName } = ${ pt.typeName }(), children: () => dom.Node = () => dom.document.createDocumentFragment()): dom.Element = {\n"
        else tracker ++= s"  def apply(props: ${ pt.typeName } = ${ pt.typeName }()): dom.Element = {\n"
      case Some(pt) =>
        if hasChildren then
          tracker ++= s"  def apply${ pt.typeParams }(props: ${ pt.typeName }, children: () => dom.Node = () => dom.document.createDocumentFragment()): dom.Element = {\n"
        else tracker ++= s"  def apply${ pt.typeParams }(props: ${ pt.typeName }): dom.Element = {\n"
      case None =>
        if hasChildren then
          tracker ++= "  def apply(children: () => dom.Node = () => dom.document.createDocumentFragment()): dom.Element = {\n"
        else tracker ++= "  def apply(): dom.Element = {\n"

    tracker ++= "    val (_result, _owner) = Owner.withNew {\n"

    if ir.style.isDefined then tracker ++= "    Style.inject(_scopeId, _css)\n"

    // ── script body ───────────────────────────────────────────────────────
    if ir.scriptBody.trim.nonEmpty then
      tracker.markSourceLine(ir.scriptBodyLine)
      ir.scriptBody.linesIterator.foreach(line => tracker ++= s"    $line\n")
      tracker += '\n'

    // ── template section ──────────────────────────────────────────────────
    val roots = ir.template

    val nodePos = ir.nodePositions

    roots match
      case Nil =>
        tracker.markSourceLine(ir.templateStartLine)
        tracker ++= "    val _result = dom.document.createElement(\"div\")\n"
      case single :: Nil =>
        val v = emitNode(single, tracker, "    ", ctr, isRoot = true, parentVar = None, nodePos = nodePos)
        if v.nonEmpty then tracker ++= s"    val _result = $v\n"
        else
          tracker.markSourceLine(ir.templateStartLine)
          tracker ++= "    val _result = dom.document.createElement(\"div\")\n"
      case multiple =>
        tracker.markSourceLine(ir.templateStartLine)
        tracker ++= "    val _root = dom.document.createElement(\"div\")\n"
        tracker ++= "    _root.classList.add(_scopeId)\n"
        multiple.foreach { node =>
          val v = emitNode(node, tracker, "    ", ctr, isRoot = false, parentVar = Some("_root"), nodePos = nodePos)
          if v.nonEmpty then tracker ++= s"    if !Hydrating.isActive then _root.appendChild($v)\n"
        }
        tracker ++= "    val _result = _root\n"

    tracker ++= "    _result\n"
    tracker ++= "    }\n"
    tracker ++= "    melt.runtime.Lifecycle.register(_result, _owner)\n"
    tracker ++= "    _result\n"
    tracker ++= "  }\n\n"

    // ── field-forwarding apply(field = ...) overload ──────────────────────
    if emitFieldApply then ir.propsType.foreach(pt => emitFieldForwardApply(tracker, pt))

    // ── mount() ───────────────────────────────────────────────────────────
    ir.propsType match
      case Some(pt) if pt.allHaveDefaults && !pt.isNamedTuple && pt.typeParams.isEmpty =>
        tracker ++= s"  def mount(target: dom.Element, props: ${ pt.typeName } = ${ pt.typeName }()): Unit = Mount(target, apply(props))\n\n"
      case Some(pt) =>
        tracker ++= s"  def mount${ pt.typeParams }(target: dom.Element, props: ${ pt.typeName }): Unit = Mount(target, apply(props))\n\n"
      case None =>
        tracker ++= "  def mount(target: dom.Element): Unit = Mount(target, apply())\n\n"

    // ── hydration entry ───────────────────────────────────────────────────
    if ir.hydration && ir.propsType.forall(pt => pt.typeParams.isEmpty && !pt.isNamedTuple) then
      val hydrationBuf = new StringBuilder
      emitHydrationEntry(hydrationBuf, ir)
      tracker ++= hydrationBuf.toString

    tracker ++= "}\n"

    // ── source-map metadata block ─────────────────────────────────────────
    val entries        = tracker.mappings()
    val safeSourcePath = ir.sourcePath.replace("\n", "").replace("\r", "").replace("*/", "*\\/").replace("/*", "/\\*")
    val meta           =
      if ir.sourcePath.nonEmpty && entries.nonEmpty then
        val v3 = melt.codegen.SourceMapV3.generateBase64(ir.sourcePath, entries)
        s"\n/*\n    -- MELT GENERATED --\n    SOURCE: $safeSourcePath\n    V3: $v3\n    -- MELT GENERATED --\n*/\n"
      else ""

    tracker.result() + meta

  // ── emitNode ──────────────────────────────────────────────────────────────

  /** Emits a single [[IrNode]] into `buf`.
    *
    * @return the variable name of the emitted DOM node, or `""` if the node
    *         appends directly to `parentVar` (or emits nothing).
    */
  private def emitNode(
    node:      IrNode,
    buf:       LineTracker,
    indent:    String,
    ctr:       Counter,
    isRoot:    Boolean,
    parentVar: Option[String],
    ns:        String = "",
    nodePos:   IrNodePositions = IrNodePositions.empty
  ): String =
    nodePos.get(node).foreach { case (l, c) => buf.markSourceLine(l, c) }
    node match

      // ── Static text ──────────────────────────────────────────────────────
      case IrNode.IrStaticText(content) =>
        if content.isBlank then ""
        else
          val v       = ctr.nextTxt()
          val escaped = escapeStr(content)
          buf ++= s"""${ indent }val $v = if Hydrating.isActive then Hydrating.textNode("$escaped") else dom.document.createTextNode("$escaped")\n"""
          v

      // ── Hoisted element reference ─────────────────────────────────────────
      case IrNode.IrHoistRef(id, tag) =>
        val v = ctr.nextEl()
        parentVar match
          case Some(p) =>
            // Capture the hydrating state BEFORE claiming. `Hydrating.element`
            // advances the cursor; when this hoisted node is the LAST child its
            // cursor becomes null, flipping `Hydrating.isActive` to false. The
            // usual caller-emitted `if !Hydrating.isActive then parent.appendChild`
            // would then wrongly append the *clone* alongside the already-claimed
            // SSR node — a visible duplicate. Guard the append on the pre-claim
            // state and handle it here (return "" so the caller does not append).
            val hy = s"${ v }_hy"
            buf ++= s"${ indent }val $hy = Hydrating.isActive\n"
            buf ++= s"""${ indent }if $hy then Hydrating.element("$tag")\n"""
            buf ++= s"${ indent }val $v = $id.cloneNode(true).asInstanceOf[dom.Element]\n"
            buf ++= s"${ indent }if !$hy then $p.appendChild($v)\n"
            ""
          case None =>
            buf ++= s"""${ indent }if Hydrating.isActive then Hydrating.element("$tag")\n"""
            buf ++= s"${ indent }val $v = $id.cloneNode(true).asInstanceOf[dom.Element]\n"
            v

      // ── Static element ────────────────────────────────────────────────────
      case IrNode.IrStaticElement(tag, irNs, attrs, children, scopeId) =>
        val childNs = resolveNs(tag, irNs, ns)
        emitElementCore(tag, childNs, scopeId, attrs, children, buf, indent, ctr, isRoot, parentVar, nodePos)

      // ── Dynamic element ───────────────────────────────────────────────────
      case IrNode.IrElement(tag, irNs, attrs, children, scopeId) =>
        val childNs = resolveNs(tag, irNs, ns)
        emitElementCore(tag, childNs, scopeId, attrs, children, buf, indent, ctr, isRoot, parentVar, nodePos)

      // ── Dynamic tag (<melt:element this={tagExpr}>) ───────────────────────
      case IrNode.IrDynamicElement(tagExpr, attrs, children, _) =>
        val anchor   = ctr.nextTxt()
        val elVar    = "_dynEl"
        val setupBuf = new LineTracker
        attrs.foreach(emitAttr(_, elVar, setupBuf, s"$indent  ", ctr))
        children.foreach { child =>
          val cv = emitNode(
            child,
            setupBuf,
            s"$indent  ",
            ctr,
            isRoot    = false,
            parentVar = Some(elVar),
            ns        = ns,
            nodePos   = nodePos
          )
          if cv.nonEmpty then setupBuf ++= s"$indent  $elVar.appendChild($cv)\n"
        }
        buf ++= s"""${ indent }val $anchor = dom.document.createComment("")\n"""
        parentVar.foreach(p => buf ++= s"${ indent }if !Hydrating.isActive then $p.appendChild($anchor)\n")
        buf ++= s"${ indent }Bind.dynamicElement(${ tagExpr.code }, $anchor, _scopeId, ($elVar: dom.Element) => {\n"
        buf ++= setupBuf.result()
        buf ++= s"${ indent }  ()\n"
        buf ++= s"${ indent }})\n"
        ""

      // ── Dynamic text ──────────────────────────────────────────────────────
      case IrNode.IrDynamicText(expr, _, _) =>
        parentVar match
          case Some(parent) =>
            buf ++= s"${ indent }Hydrating.text(${ expr.code }, $parent)\n"
            ""
          case None =>
            val v = ctr.nextTxt()
            buf ++= s"""${ indent }val $v = if Hydrating.isActive then Hydrating.textNode((${ expr.code }).toString) else dom.document.createTextNode((${ expr.code }).toString)\n"""
            v

      // ── List rendering ────────────────────────────────────────────────────
      case IrNode.IrList(sourceExpr, renderFn) =>
        val anchor = ctr.nextTxt()
        parentVar match
          case Some(p) => buf ++= s"${ indent }val $anchor = Hydrating.dynAnchor($p.asInstanceOf[dom.Element])\n"
          case None    => buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
        buf ++= s"${ indent }Hydrating.withCursor(new HydrationCursor(null)) {\n"
        buf ++= s"${ indent }  Bind.list(${ sourceExpr.code }, ${ renderFn.code }, $anchor)\n"
        buf ++= s"${ indent }}\n"
        ""

      case IrNode.IrKeyedList(sourceExpr, keyFn, renderFn) =>
        val anchor = ctr.nextTxt()
        parentVar match
          case Some(p) => buf ++= s"${ indent }val $anchor = Hydrating.dynAnchor($p.asInstanceOf[dom.Element])\n"
          case None    => buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
        buf ++= s"${ indent }Hydrating.withCursor(new HydrationCursor(null)) {\n"
        buf ++= s"${ indent }  Bind.each(${ sourceExpr.code }, ${ keyFn.code }, ${ renderFn.code }, $anchor)\n"
        buf ++= s"${ indent }}\n"
        ""

      // ── Conditional rendering ─────────────────────────────────────────────
      case IrNode.IrConditional(sourceOpt, condAndBody) =>
        val anchor = ctr.nextTxt()
        parentVar match
          case Some(p) => buf ++= s"${ indent }val $anchor = Hydrating.dynAnchor($p.asInstanceOf[dom.Element])\n"
          case None    => buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
        buf ++= s"${ indent }Hydrating.withCursor(new HydrationCursor(null)) {\n"
        sourceOpt match
          case Some(src) => buf ++= s"${ indent }  Bind.show(${ src.code }, _ => { ${ condAndBody.code } }, $anchor)\n"
          case None      => buf ++= s"${ indent }  Bind.show(() => { ${ condAndBody.code } }, $anchor)\n"
        buf ++= s"${ indent }}\n"
        ""

      // ── Raw HTML ──────────────────────────────────────────────────────────
      case IrNode.IrRawHtml(sourceOpt, expr) =>
        val anchor = ctr.nextTxt()
        buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt-html")\n"""
        parentVar.foreach(p => buf ++= s"${ indent }if !Hydrating.isActive then $p.appendChild($anchor)\n")
        sourceOpt match
          case Some(src) => buf ++= s"${ indent }Bind.htmlAnchor(${ src.code }, _ => { ${ expr.code } }, $anchor)\n"
          // SSR inlines TrustedHtml content directly with no anchor marker, so during
          // hydration the content is already in the DOM — skip re-insertion.
          case None => buf ++= s"${ indent }if !Hydrating.isActive then Bind.htmlAnchor(${ expr.code }, $anchor)\n"
        ""

      // ── DOM-returning expressions ─────────────────────────────────────────
      case IrNode.IrDomResult(expr) =>
        val v = ctr.nextEl()
        buf ++= s"${ indent }val $v: dom.Element = {\n${ indent }  ${ expr.code }\n${ indent }}\n"
        v

      case IrNode.IrFragmentResult(expr) =>
        val v = ctr.nextEl()
        buf ++= s"${ indent }val $v: dom.Node = {\n${ indent }  ${ expr.code }\n${ indent }}\n"
        v

      // ── Component ─────────────────────────────────────────────────────────
      case IrNode.IrComponent(name, props, children, spreadExpr, hasStyled, bindThisExpr) =>
        val v = ctr.nextEl()

        spreadExpr match
          case Some(expr) =>
            buf ++= s"${ indent }val $v = $name(${ expr.code })\n"
          case None =>
            // Emit snippet children separately (they become extra Props args)
            val (snippetProps, regularChildren) = children match
              case None       => (Nil, None)
              case Some(slot) =>
                val (snips, regs) = slot.nodes.partition { case IrNode.IrSnippetDef(_, _, _) => true; case _ => false }
                val snippetArgs   = snips.collect {
                  case IrNode.IrSnippetDef(snName, snParams, snChildren) =>
                    val varName = s"_snippet_${ snName }_${ ctr.nextChildIdx() }"
                    emitSnippetDef(buf, varName, snParams, snChildren, indent, ctr, nodePos)
                    IrProp(snName, IrPropValue.Dynamic(ScalaExpr(varName)))
                }
                (snippetArgs, if regs.nonEmpty then Some(IrChildrenSlot(regs)) else None)

            val childrenVar: Option[String] = regularChildren.map { slot =>
              emitChildrenLambda(slot.nodes, indent, ctr, buf, nodePos)
            }

            val allArgs = (props.map(p => s"${ p.name } = ${ emitPropValue(p.value) }") ++
              snippetProps.map(p => s"${ p.name } = ${ emitPropValue(p.value) }")).mkString(", ")

            (allArgs, childrenVar) match
              case ("", None)     => buf ++= s"${ indent }val $v = $name()\n"
              case ("", Some(cv)) => buf ++= s"${ indent }val $v = $name(children = $cv)\n"
              case (pa, None)     => buf ++= s"${ indent }val $v = $name($name.Props($pa))\n"
              case (pa, Some(cv)) => buf ++= s"${ indent }val $v = $name($name.Props($pa), children = $cv)\n"

        if hasStyled then buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        bindThisExpr.foreach { expr => buf ++= s"${ indent }${ expr.code }.set($v)\n" }
        v

      // ── {children} expression ─────────────────────────────────────────────
      case IrNode.IrChildren =>
        val v = ctr.nextEl()
        buf ++= s"${ indent }val $v: dom.Node = children()\n"
        v

      // ── InlineTemplate bridge ─────────────────────────────────────────────
      case IrNode.IrInlineTemplate(parts) =>
        emitInlineTemplate(parts, buf, indent, ctr, isRoot, parentVar, ns, nodePos)

      // ── <melt:head> ───────────────────────────────────────────────────────
      case IrNode.IrHead(children) =>
        children.foreach { child =>
          val cv = emitNode(child, buf, indent, ctr, isRoot = false, parentVar = None, ns = ns, nodePos = nodePos)
          if cv.nonEmpty then buf ++= s"${ indent }Head.appendChild($cv)\n"
        }
        ""

      // ── <melt:window> ─────────────────────────────────────────────────────
      case IrNode.IrWindow(attrs) =>
        attrs.foreach {
          case IrAttr.EventHandler(event, handler) =>
            buf ++= s"""${ indent }Window.on("$event")(${ handler.code })\n"""
          case IrAttr.BindWindow(prop, expr) =>
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
            buf ++= s"${ indent }Window.$method(${ expr.code })\n"
          case _ =>
        }
        ""

      // ── <melt:body> ───────────────────────────────────────────────────────
      case IrNode.IrBody(attrs) =>
        attrs.foreach {
          case IrAttr.EventHandler(event, handler) =>
            buf ++= s"""${ indent }Body.on("$event")(${ handler.code })\n"""
          case IrAttr.UseAction(actionName, Some(params)) =>
            buf ++= s"${ indent }Bind.action(dom.document.body, $actionName, ${ params.code })\n"
          case IrAttr.UseAction(actionName, None) =>
            buf ++= s"${ indent }Bind.action(dom.document.body, $actionName, ())\n"
          case _ =>
        }
        ""

      // ── <melt:document> ───────────────────────────────────────────────────
      case IrNode.IrDocument(attrs) =>
        attrs.foreach {
          case IrAttr.EventHandler(event, handler) =>
            buf ++= s"""${ indent }Document.on("$event")(${ handler.code })\n"""
          case IrAttr.BindDocument(prop, expr) =>
            val method = prop match
              case "visibilityState"    => "bindVisibilityState"
              case "fullscreenElement"  => "bindFullscreenElement"
              case "pointerLockElement" => "bindPointerLockElement"
              case "activeElement"      => "bindActiveElement"
              case other                => s"bind${ other.capitalize }"
            buf ++= s"${ indent }Document.$method(${ expr.code })\n"
          case _ =>
        }
        ""

      // ── <melt:boundary> ───────────────────────────────────────────────────
      case IrNode.IrBoundary(children, pending, failed, onError) =>
        val idx   = ctr.nextChildIdx()
        val inner = indent + "  "

        val pendingProp: String = pending match
          case None            => ""
          case Some(pChildren) =>
            val pVar = s"_bPending$idx"
            buf ++= s"${ indent }val $pVar: (() => dom.Element) = () => {\n"
            emitBoundaryBody(buf, pChildren, inner, ctr, nodePos)
            buf ++= s"${ indent }}\n"
            s", pending = Some($pVar)"

        val fallbackProp: String = failed match
          case None     => ""
          case Some(fb) =>
            val fVar = s"_bFallback$idx"
            buf ++= s"${ indent }val $fVar: (Throwable, () => Unit) => dom.Element = (${ fb.errorVar }, ${ fb.resetVar }) => {\n"
            emitBoundaryBody(buf, fb.children, inner, ctr, nodePos)
            buf ++= s"${ indent }}\n"
            s", fallback = $fVar"

        val onErrorProp: String = onError.map(e => s", onError = ${ e.code }").getOrElse("")

        val cVar = s"_bChildren$idx"
        buf ++= s"${ indent }val $cVar: (() => dom.Element) = () => {\n"
        emitBoundaryBody(buf, children, inner, ctr, nodePos)
        buf ++= s"${ indent }}\n"

        val fragVar = s"_bFrag$idx"
        buf ++= s"${ indent }val $fragVar = Boundary.create(Boundary.Props(children = $cVar$pendingProp$fallbackProp$onErrorProp))\n"

        parentVar match
          case Some(parent) =>
            buf ++= s"${ indent }if !Hydrating.isActive then $parent.appendChild($fragVar)\n"
            ""
          case None =>
            val wVar = s"_bWrap$idx"
            buf ++= s"${ indent }val $wVar = dom.document.createElement(\"div\")\n"
            buf ++= s"""${ indent }$wVar.setAttribute("style", "display: contents")\n"""
            buf ++= s"${ indent }$wVar.appendChild($fragVar)\n"
            wVar

      // ── <melt:await> async boundary (client = reactive on the query state) ──
      case IrNode.IrAwait(valueExpr, handler, pending, _) =>
        val anchor = ctr.nextTxt()
        parentVar match
          case Some(p) => buf ++= s"${ indent }val $anchor = Hydrating.dynAnchor($p.asInstanceOf[dom.Element])\n"
          case None    => buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""

        // The handler `{ case Async.Done(x) => <html> … }` reconstructed as a
        // DOM-returning partial function; the `pending` slot supplies the Loading
        // branch (so the user need not write a Loading arm). The body re-reads the
        // reactive query state, so `x` stays fully typed.
        val handlerPf   = reconstructInlineExpr(handler, indent + "      ", ns, nodePos)
        val pendingExpr = pending match
          case Some(pNodes) =>
            reconstructInlineExpr(List(IrInlineTemplatePart.Html(pNodes)), indent + "      ", ns, nodePos)
          case None => "dom.document.createTextNode(\"\")"

        // Re-indent the reconstructed arms to one consistent column. The handler's
        // Code parts carry the user's indentation while the Html parts are emitted at
        // the emitter's, so the raw splice is ragged and trips the layout parser;
        // explicit braces make the DOM blocks indentation-insensitive, so flattening
        // to a single column is safe (and keeps `//` comments on their own line).
        val armIndent   = indent + "      "
        val handlerArms = handlerPf.linesIterator
          .map(l => if l.trim.isEmpty then "" else armIndent + l.trim)
          .filter(_.nonEmpty)
          .mkString("\n")

        // Loading is the explicit `pending` arm, so if the handler covers Done and
        // Failed the match already exhausts the sealed `Async` — a `case _` fallback
        // would be an unreachable case. Emit it only for a partial handler.
        val handlerCode   = handler.collect { case IrInlineTemplatePart.Code(c) => c }.mkString(" ")
        val spaExhaustive = handlerCode.contains("Done") && handlerCode.contains("Failed")

        buf ++= s"${ indent }Hydrating.withCursor(new HydrationCursor(null)) {\n"
        buf ++= s"${ indent }  Bind.show(${ valueExpr.code }.state, _ => {\n"
        // Splice the handler's `case …` arms straight into the match on the (typed)
        // query state. (A partial-function literal as a receiver would leave its
        // scrutinee type uninferred.)
        buf ++= s"${ indent }    ${ valueExpr.code }.state.value match {\n"
        buf ++= s"${ indent }      case _root_.melt.runtime.Async.Loading => $pendingExpr\n"
        buf ++= s"$handlerArms\n"
        if !spaExhaustive then buf ++= s"$armIndent case _ => dom.document.createTextNode(\"\")\n"
        buf ++= s"${ indent }    }\n"
        buf ++= s"${ indent }  }, $anchor)\n"
        buf ++= s"${ indent }}\n"
        ""

      // ── <melt:key> ────────────────────────────────────────────────────────
      case IrNode.IrKeyBlock(keyExpr, children) =>
        val idx       = ctr.nextChildIdx()
        val inner     = indent + "  "
        val kVar      = s"_keyRender$idx"
        val startAnch = ctr.nextTxt()
        val endAnch   = ctr.nextTxt()

        buf ++= s"${ indent }val $kVar: (() => dom.DocumentFragment) = () => {\n"
        emitKeyBody(buf, children, inner, ctr, nodePos)
        buf ++= s"${ indent }}\n"

        buf ++= s"""${ indent }val $startAnch = dom.document.createComment("melt-key-start")\n"""
        buf ++= s"""${ indent }val $endAnch   = dom.document.createComment("melt-key-end")\n"""
        parentVar.foreach { p =>
          buf ++= s"${ indent }if !Hydrating.isActive then $p.appendChild($startAnch)\n"
          buf ++= s"${ indent }if !Hydrating.isActive then $p.appendChild($endAnch)\n"
        }
        buf ++= s"${ indent }Bind.key(${ keyExpr.code }, $kVar, $startAnch, $endAnch)\n"
        ""

      // ── {#snippet} ────────────────────────────────────────────────────────
      case IrNode.IrSnippetDef(name, params, children) =>
        emitSnippetDef(buf, name, params, children, indent, ctr, nodePos)
        ""

      // ── {@render} ─────────────────────────────────────────────────────────
      case IrNode.IrRenderCall(expr) =>
        val v = ctr.nextEl()
        buf ++= s"${ indent }val $v = ${ expr.code }\n"
        v

  // ── Element emission ───────────────────────────────────────────────────────

  private def emitElementCore(
    tag:       String,
    childNs:   String,
    scopeId:   String,
    attrs:     List[IrAttr],
    children:  List[IrNode],
    buf:       LineTracker,
    indent:    String,
    ctr:       Counter,
    isRoot:    Boolean,
    parentVar: Option[String],
    nodePos:   IrNodePositions = IrNodePositions.empty
  ): String =
    val v = ctr.nextEl()
    if childNs == "svg" then
      buf ++= s"""${ indent }val $v = if Hydrating.isActive then Hydrating.elementNS("$SvgNs", "$tag") else dom.document.createElementNS("$SvgNs", "$tag")\n"""
    else if childNs == "math" then
      buf ++= s"""${ indent }val $v = if Hydrating.isActive then Hydrating.elementNS("$MathNs", "$tag") else dom.document.createElementNS("$MathNs", "$tag")\n"""
    else
      buf ++= s"""${ indent }val $v = if Hydrating.isActive then Hydrating.element("$tag") else dom.document.createElement("$tag")\n"""
    buf ++= s"${ indent }$v.classList.add(_scopeId)\n"

    // <select bind:value> must be emitted AFTER children so the initial value finds a matching <option>
    val deferredSelectBind: Option[IrAttr.BindSelectValue] =
      if tag.equalsIgnoreCase("select") then attrs.collectFirst { case bsv: IrAttr.BindSelectValue => bsv } else None

    attrs.foreach { attr =>
      attr match
        case _: IrAttr.BindSelectValue if deferredSelectBind.isDefined => () // emitted after children
        case _                                                         => emitAttr(attr, v, buf, indent, ctr)
    }

    val hasChildren = children.nonEmpty
    val childIndent = if hasChildren then indent + "  " else indent
    if hasChildren then buf ++= s"${ indent }Hydrating.withChildren($v) {\n"

    // mergeGroup-annotated IrDynamicText nodes are declared first (no inline subscribe),
    // then a single merged subscription is emitted per reactive var at the end of the block.
    val mergeGroups = mutable.LinkedHashMap.empty[String, mutable.ListBuffer[(String, String)]]
    children.foreach { child =>
      child match
        case IrNode.IrDynamicText(expr, _, Some(varName)) =>
          val tv       = ctr.nextTxt()
          val initExpr = if expr.code.trim == varName then s"$varName.value" else expr.code
          buf ++= s"${ childIndent }val $tv = Hydrating.text(($initExpr).toString, $v)\n"
          mergeGroups.getOrElseUpdate(varName, mutable.ListBuffer.empty) += ((tv, initExpr))
        case other =>
          val cv =
            emitNode(other, buf, childIndent, ctr, isRoot = false, parentVar = Some(v), ns = childNs, nodePos = nodePos)
          if cv.nonEmpty then buf ++= s"${ childIndent }if !Hydrating.isActive then $v.appendChild($cv)\n"
    }

    // Emit one merged subscription per reactive var, inside the withChildren block
    mergeGroups.foreach {
      case (varName, nodes) =>
        val cancelVar = ctr.nextEl()
        buf ++= s"${ childIndent }val $cancelVar = $varName.subscribe { _ =>\n"
        nodes.foreach {
          case (tv, updateExpr) =>
            buf ++= s"${ childIndent }  $tv.textContent = ($updateExpr).toString\n"
        }
        buf ++= s"${ childIndent }}\n"
        buf ++= s"${ childIndent }Cleanup.register($cancelVar)\n"
    }

    if hasChildren then buf ++= s"${ indent }}\n"

    deferredSelectBind.foreach { bsv =>
      if bsv.multiple then
        buf ++= s"${ indent }Bind.selectMultipleValue($v.asInstanceOf[dom.html.Select], ${ bsv.expr.code })\n"
      else buf ++= s"${ indent }Bind.selectValue($v.asInstanceOf[dom.html.Select], ${ bsv.expr.code })\n"
    }
    v

  // ── emitAttr ──────────────────────────────────────────────────────────────

  private def emitAttr(attr: IrAttr, v: String, buf: LineTracker, indent: String, ctr: Counter): Unit =
    attr match
      case IrAttr.StaticAttr("class", value) =>
        value.split("\\s+").filter(_.nonEmpty).foreach { cls =>
          buf ++= s"""${ indent }$v.classList.add("${ escapeStr(cls) }")\n"""
        }
      case IrAttr.StaticAttr(name, value) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", "${ escapeStr(value) }")\n"""
      case IrAttr.BooleanAttr(name) =>
        buf ++= s"""${ indent }$v.setAttribute("$name", "")\n"""
      case IrAttr.DynamicAttr(name, expr) =>
        buf ++= s"""${ indent }Bind.attr($v, "$name", ${ expr.code })\n"""
      case IrAttr.DynamicBooleanAttr(name, expr) =>
        buf ++= s"""${ indent }Bind.booleanAttr($v, "$name", ${ expr.code })\n"""
      case IrAttr.DynamicClass(expr) =>
        buf ++= s"${ indent }Bind.cls($v, ${ expr.code })\n"
      case IrAttr.Spread(expr) =>
        buf ++= s"${ indent }${ expr.code }.apply($v)\n"

      case IrAttr.EventHandler(event, handler) =>
        buf ++= s"""${ indent }$v.addEventListener("$event", ${ handler.code })\n"""
      case IrAttr.EventHandlerWithModifier(event, handler, mods) =>
        if mods.contains("preventDefault") then
          buf ++= s"""${ indent }$v.addEventListener("$event", ((e: dom.Event) => { e.preventDefault(); (${ handler.code }).asInstanceOf[Any] }))\n"""
        else
          buf ++= s"""${ indent }$v.addEventListener("$event", ((_: dom.Event) => (${ handler.code }).asInstanceOf[Any]))\n"""

      case IrAttr.BindInputValue(expr) =>
        buf ++= s"${ indent }Bind.inputValue($v.asInstanceOf[dom.html.Input], ${ expr.code })\n"
      case IrAttr.BindTextareaValue(expr) =>
        buf ++= s"${ indent }Bind.textareaValue($v.asInstanceOf[dom.html.TextArea], ${ expr.code })\n"
      case IrAttr.BindSelectValue(expr, false) =>
        buf ++= s"${ indent }Bind.selectValue($v.asInstanceOf[dom.html.Select], ${ expr.code })\n"
      case IrAttr.BindSelectValue(expr, true) =>
        buf ++= s"${ indent }Bind.selectMultipleValue($v.asInstanceOf[dom.html.Select], ${ expr.code })\n"
      case IrAttr.BindInputValueInt(expr) =>
        buf ++= s"${ indent }Bind.inputInt($v.asInstanceOf[dom.html.Input], ${ expr.code })\n"
      case IrAttr.BindInputValueDouble(expr) =>
        buf ++= s"${ indent }Bind.inputDouble($v.asInstanceOf[dom.html.Input], ${ expr.code })\n"
      case IrAttr.BindChecked(expr) =>
        buf ++= s"${ indent }Bind.inputChecked($v.asInstanceOf[dom.html.Input], ${ expr.code })\n"
      case IrAttr.BindGroup(expr, false) =>
        buf ++= s"${ indent }Bind.radioGroup($v.asInstanceOf[dom.html.Input], ${ expr.code }, $v.asInstanceOf[dom.html.Input].value)\n"
      case IrAttr.BindGroup(expr, true) =>
        buf ++= s"${ indent }Bind.checkboxGroup($v.asInstanceOf[dom.html.Input], ${ expr.code }, $v.asInstanceOf[dom.html.Input].value)\n"
      case IrAttr.BindThis(expr) =>
        buf ++= s"${ indent }${ expr.code }.set($v.asInstanceOf[dom.Element])\n"
      case IrAttr.BindDimension(property, expr) =>
        buf ++= s"${ indent }Bind.$property($v, ${ expr.code })\n"
      case IrAttr.BindInnerHtml(expr) =>
        buf ++= s"${ indent }Bind.html($v, ${ expr.code })\n"
      case IrAttr.BindTextContent(expr) =>
        buf ++= s"${ indent }Bind.textContent($v, ${ expr.code })\n"
      case IrAttr.BindMedia(property, expr) =>
        val method = property match
          case "currentTime"  => "mediaCurrentTime"
          case "duration"     => "mediaDuration"
          case "paused"       => "mediaPaused"
          case "volume"       => "mediaVolume"
          case "muted"        => "mediaMuted"
          case "playbackRate" => "mediaPlaybackRate"
          case "seeking"      => "mediaSeeking"
          case "ended"        => "mediaEnded"
          case "readyState"   => "mediaReadyState"
          case "videoWidth"   => "mediaVideoWidth"
          case "videoHeight"  => "mediaVideoHeight"
          case other          => s"media${ other.capitalize }"
        buf ++= s"${ indent }Bind.$method($v, ${ expr.code })\n"

      case IrAttr.ClassToggle(name, expr) =>
        buf ++= s"""${ indent }Bind.classToggle($v, "$name", ${ expr.code })\n"""
      case IrAttr.StyleProp(property, expr) =>
        buf ++= s"""${ indent }Bind.style($v, "$property", ${ expr.code })\n"""

      case IrAttr.UseAction(actionName, Some(params)) =>
        buf ++= s"${ indent }Bind.action($v, $actionName, ${ params.code })\n"
      case IrAttr.UseAction(actionName, None) =>
        buf ++= s"${ indent }Bind.action($v, $actionName, ())\n"

      case IrAttr.Transition(direction, name, paramsOpt, global) =>
        val params = paramsOpt.map(_.code).getOrElse("TransitionParams.default")
        val obj    = name.capitalize
        direction match
          case TransitionDirection.Both => buf ++= s"${ indent }TransitionBridge.setBoth($v, $obj, $params)\n"
          case TransitionDirection.In   => buf ++= s"${ indent }TransitionBridge.setIn($v, $obj, $params)\n"
          case TransitionDirection.Out  => buf ++= s"${ indent }TransitionBridge.setOut($v, $obj, $params)\n"
        if global then
          buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltGlobal")(true)\n"""

      case IrAttr.Animate(name, paramsOpt) =>
        val fn     = name.capitalize
        val params = paramsOpt.map(_.code).getOrElse("AnimateParams()")
        buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltAnimateFn")(($fn: AnimateFn).asInstanceOf[scalajs.js.Any])\n"""
        buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltAnimateParams")(($params: AnimateParams).asInstanceOf[scalajs.js.Any])\n"""

      case IrAttr.BindWindow(_, _) | IrAttr.BindDocument(_, _) =>
        () // handled by IrWindow / IrDocument emitNode cases

  // ── Helper emitters ────────────────────────────────────────────────────────

  /** Emits an `IrInlineTemplate` by reconstructing the mixed Scala+HTML expression,
    * then re-classifying it with [[AstToIr.lowerExpression]] and recursing into `emitNode`.
    */
  private def emitInlineTemplate(
    parts:     List[IrInlineTemplatePart],
    buf:       LineTracker,
    indent:    String,
    ctr:       Counter,
    isRoot:    Boolean,
    parentVar: Option[String],
    ns:        String,
    nodePos:   IrNodePositions = IrNodePositions.empty
  ): String =
    // Re-classify the reconstructed expression and emit it
    val irNode = AstToIr.lowerExpression(reconstructInlineExpr(parts, indent, ns, nodePos))
    emitNode(irNode, buf, indent, ctr, isRoot, parentVar, ns, nodePos)

  /** Reconstructs an inline-template part list into a single Scala expression string,
    * with each `Html` fragment lowered to a self-contained DOM-building block (a single
    * node, or a `DocumentFragment` for multiple). `Code` parts are emitted verbatim, so
    * the result preserves any surrounding Scala syntax (e.g. a `{ case … => <html> }`
    * partial function). Used by [[emitInlineTemplate]] and the `<melt:await>` handler. */
  private def reconstructInlineExpr(
    parts:   List[IrInlineTemplatePart],
    indent:  String,
    ns:      String,
    nodePos: IrNodePositions
  ): String =
    val exprBuf = new StringBuilder
    parts.foreach {
      case IrInlineTemplatePart.Code(code) =>
        exprBuf ++= code
      case IrInlineTemplatePart.Html(nodes) =>
        val innerBuf = new LineTracker
        val innerCtr = new Counter
        nodes match
          case Nil =>
            exprBuf ++= "dom.document.createTextNode(\"\")"
          case single :: Nil =>
            val v = emitNode(
              single,
              innerBuf,
              indent + "  ",
              innerCtr,
              isRoot    = false,
              parentVar = None,
              ns        = ns,
              nodePos   = nodePos
            )
            exprBuf ++= s"{\n${ innerBuf.result() }${ indent }  $v\n${ indent }}"
          case multiple =>
            innerBuf ++= s"${ indent }  val _frag = dom.document.createDocumentFragment()\n"
            multiple.foreach { n =>
              val v = emitNode(
                n,
                innerBuf,
                indent + "  ",
                innerCtr,
                isRoot    = false,
                parentVar = Some("_frag"),
                ns        = ns,
                nodePos   = nodePos
              )
              if v.nonEmpty then innerBuf ++= s"${ indent }  _frag.appendChild($v)\n"
            }
            exprBuf ++= s"{\n${ innerBuf.result() }${ indent }  _frag\n${ indent }}"
    }
    exprBuf.toString

  /** Emits a `val _childrenN = () => { ... }` lambda. Returns the variable name. */
  private def emitChildrenLambda(
    nodes:   List[IrNode],
    indent:  String,
    ctr:     Counter,
    buf:     LineTracker,
    nodePos: IrNodePositions = IrNodePositions.empty
  ): String =
    val varName = s"_children${ ctr.nextChildIdx() }"
    val inner   = indent + "  "
    buf ++= s"${ indent }val $varName: (() => dom.Node) = () => {\n"
    buf ++= s"${ inner }val _frag = dom.document.createDocumentFragment()\n"
    nodes.foreach { child =>
      val cv = emitNode(child, buf, inner, ctr, isRoot = false, parentVar = Some("_frag"), nodePos = nodePos)
      if cv.nonEmpty then buf ++= s"${ inner }if !Hydrating.isActive then _frag.appendChild($cv)\n"
    }
    buf ++= s"${ inner }_frag\n"
    buf ++= s"${ indent }}\n"
    varName

  private def emitSnippetDef(
    buf:      LineTracker,
    name:     String,
    params:   List[IrSnippetParam],
    children: List[IrNode],
    indent:   String,
    ctr:      Counter,
    nodePos:  IrNodePositions = IrNodePositions.empty
  ): Unit =
    val inner    = indent + "  "
    val innerCtr = new Counter

    val (typeStr, paramStr) = params match
      case Nil =>
        ("() => dom.Node", "()")
      case List(p) =>
        val tpe   = p.typeAnnotation.getOrElse("Any")
        val pDecl = p.typeAnnotation.map(t => s"${ p.name }: $t").getOrElse(p.name)
        (s"($tpe) => dom.Node", s"($pDecl)")
      case ps =>
        val types = ps.map(_.typeAnnotation.getOrElse("Any")).mkString(", ")
        val decls = ps.map(p => p.typeAnnotation.map(t => s"${ p.name }: $t").getOrElse(p.name)).mkString(", ")
        (s"(($types)) => dom.Node", s"(($decls))")

    buf ++= s"${ indent }val $name: $typeStr = $paramStr => {\n"

    children match
      case Nil =>
        buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case single :: Nil =>
        val cv = emitNode(single, buf, inner, innerCtr, isRoot = false, parentVar = None, nodePos = nodePos)
        if cv.nonEmpty then buf ++= s"${ inner }$cv\n"
        else buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case multiple =>
        buf ++= s"${ inner }val _frag = dom.document.createDocumentFragment()\n"
        multiple.foreach { child =>
          val cv = emitNode(child, buf, inner, innerCtr, isRoot = false, parentVar = Some("_frag"), nodePos = nodePos)
          if cv.nonEmpty then buf ++= s"${ inner }_frag.appendChild($cv)\n"
        }
        buf ++= s"${ inner }_frag\n"

    buf ++= s"${ indent }}\n"

  private def emitBoundaryBody(
    buf:      LineTracker,
    children: List[IrNode],
    inner:    String,
    ctr:      Counter,
    nodePos:  IrNodePositions = IrNodePositions.empty
  ): Unit =
    children match
      case Nil =>
        buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case single :: Nil =>
        val cv = emitNode(single, buf, inner, ctr, isRoot = false, parentVar = None, nodePos = nodePos)
        if cv.nonEmpty then buf ++= s"${ inner }$cv\n"
        else buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case multiple =>
        buf ++= s"${ inner }val _bFrag = dom.document.createElement(\"div\")\n"
        multiple.foreach { child =>
          val cv = emitNode(child, buf, inner, ctr, isRoot = false, parentVar = Some("_bFrag"), nodePos = nodePos)
          if cv.nonEmpty then buf ++= s"${ inner }_bFrag.appendChild($cv)\n"
        }
        buf ++= s"${ inner }_bFrag\n"

  private def emitKeyBody(
    buf:      LineTracker,
    children: List[IrNode],
    inner:    String,
    ctr:      Counter,
    nodePos:  IrNodePositions = IrNodePositions.empty
  ): Unit =
    buf ++= s"${ inner }val _kFrag = dom.document.createDocumentFragment()\n"
    children.foreach { child =>
      val cv = emitNode(child, buf, inner, ctr, isRoot = false, parentVar = Some("_kFrag"), nodePos = nodePos)
      if cv.nonEmpty then buf ++= s"${ inner }_kFrag.appendChild($cv)\n"
    }
    buf ++= s"${ inner }_kFrag\n"

  // ── Hydration entry ───────────────────────────────────────────────────────

  private def emitHydrationEntry(buf: StringBuilder, ir: IrComponent): Unit =
    val moduleId = kebabCase(ir.objectName)

    val propsDefaults = ir.propsType.map(_.allHaveDefaults).getOrElse(true)
    val mountExpr     = ir.propsType match
      case None    => "apply()"
      case Some(_) => "apply(_props)"

    val resolveProps: String = ir.propsType match
      case None     => ""
      case Some(pt) =>
        val tpe      = pt.typeName
        val fallback =
          if propsDefaults then s"""            dom.console.warn(
               |              "[melt] hydrate($moduleId): no <script data-melt-props=\\"$moduleId\\"> " +
               |              "payload found, falling back to $tpe() defaults."
               |            )
               |            $tpe()""".stripMargin
          else s"""            dom.console.warn(
               |              "[melt] hydrate($moduleId) skipped: no <script data-melt-props=\\"$moduleId\\"> " +
               |              "payload found and $tpe has required fields."
               |            )
               |            null""".stripMargin
        s"""
           |    val _props: $tpe =
           |      val _tag = dom.document.querySelector("script[data-melt-props=\\"$moduleId\\"]")
           |      if _tag != null then
           |        try _propsCodec.decode(SimpleJson.parse(_tag.textContent))
           |        catch
           |          case t: Throwable =>
           |            dom.console.warn(
           |              "[melt] hydrate($moduleId): failed to decode Props payload (" + t.getMessage + ")"
           |            )
           |            ${ if propsDefaults then s"$tpe()" else "null" }
           |      else
           |$fallback
           |""".stripMargin

    val (guardOpen, guardClose) = ir.propsType match
      case Some(_) if !propsDefaults => ("    if _props != null then\n", "")
      case _                         => ("", "")

    val loopIndent = if ir.propsType.isDefined && !propsDefaults then "      " else "    "

    val loopBody =
      s"""${ loopIndent }starts.zip(ends).foreach { case (startNode, endNode) =>
         |${ loopIndent }  val cursor = new HydrationCursor(startNode.nextSibling)
         |${ loopIndent }  Hydrating.withCursor(cursor) {
         |${ loopIndent }    $mountExpr
         |${ loopIndent }  }
         |${ loopIndent }  Hydrating.flush()
         |${ loopIndent }}""".stripMargin

    buf ++= s"""  /** Hydration entry exported as `$moduleId.js` via the
                |    * sbt/Scala.js asset pipeline.
                |    */
                |  @JSExportTopLevel("hydrate", moduleID = "$moduleId")
                |  def _meltHydrateEntry(): Unit =
                |    val startMarker = "[melt:$moduleId"
                |    val endMarker   = "]melt:$moduleId"
                |    val walker      = dom.document.createTreeWalker(
                |      dom.document.body,
                |      dom.NodeFilter.SHOW_COMMENT,
                |      null,
                |      false
                |    )
                |    val starts = scala.collection.mutable.ListBuffer.empty[dom.Node]
                |    val ends   = scala.collection.mutable.ListBuffer.empty[dom.Node]
                |    var cur: dom.Node = walker.nextNode()
                |    while cur != null do
                |      val text = cur.asInstanceOf[dom.Comment].data
                |      if text.startsWith(startMarker) then starts += cur
                |      else if text.startsWith(endMarker) then ends += cur
                |      cur = walker.nextNode()
                |$resolveProps$guardOpen$loopBody$guardClose
                |
                |""".stripMargin

  // ── Named Tuple Props factory ──────────────────────────────────────────────

  /** Emits an `object Props { def apply(...): ComponentName.Props = (...) }` factory
    * for Named Tuple Props so that call sites can use `ComponentName.Props(field = value)`.
    *
    * ⚠ The return type must be qualified (`objectName.Props`) because inside `object Props`
    * the unqualified `Props` refers to the object itself, not the type alias.
    */
  private def emitNamedTuplePropsFactory(pt: IrPropsType, tracker: LineTracker, objectName: String): Unit =
    val paramList = pt.namedTupleFields.map { case (name, tpe) => s"$name: $tpe" }.mkString(", ")
    val argList   = pt.namedTupleFields.map { case (name, _) => s"$name = $name" }.mkString(", ")
    val retType   = s"$objectName.Props${ pt.typeParams }"
    tracker ++= s"  object Props:\n"
    tracker ++= s"    def apply${ pt.typeParams }($paramList): $retType =\n"
    tracker ++= s"      ($argList)\n"
    tracker += '\n'

  // ── Utilities ──────────────────────────────────────────────────────────────

  private def emitPropValue(v: IrPropValue): String = v match
    case IrPropValue.Static(value)   => s""""${ escapeStr(value) }""""
    case IrPropValue.Dynamic(expr)   => expr.code
    case IrPropValue.Shorthand(name) => name
    case IrPropValue.BooleanTrue     => "true"

  private def resolveNs(tag: String, irNs: Option[String], parentNs: String): String =
    irNs match
      case Some(n) => n
      case None    =>
        if parentNs == "svg" && KnownSvgTags.contains(tag) then "svg"
        else if parentNs == "math" && KnownMathTags.contains(tag) then "math"
        else parentNs

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
      case IrNode.IrAwait(_, handler, p, f) =>
        handler.exists {
          case IrInlineTemplatePart.Html(ns) => templateHasChildrenRef(ns)
          case _                             => false
        } ||
        p.exists(templateHasChildrenRef) ||
        f.exists(fb => templateHasChildrenRef(fb.children))
      case _ => false
    }

  /** Emits a field-forwarding `apply(field = ..., ...)` overload that constructs
    * `Props` and delegates to `apply(props)`, so call sites can write
    * `Component(basePath = x, lang = y)` in addition to `Component(Props(...))`.
    * No-op unless `Props` is a plain (non-generic) case class we can parse.
    */
  private def emitFieldForwardApply(tracker: LineTracker, pt: IrPropsType): Unit =
    val fields = pt.fieldForwardApplyFields
    if fields.nonEmpty then
      val params    = fields.map(f => f.default.fold(s"${ f.name }: ${ f.tpe }")(d => s"${ f.name }: ${ f.tpe } = $d"))
      val propsArgs = fields.map(f => s"${ f.name } = ${ f.name }").mkString(", ")
      tracker ++= s"  def apply(${ params.mkString(", ") }): dom.Element = apply(Props($propsArgs))\n\n"

  private def escapeStr(s: String): String =
    s.replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

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

  // ── Hoisted static element emission ─────────────────────────────────────

  private def emitHoistedNodes(hoisted: List[IrHoistedNode], tracker: LineTracker): Unit =
    hoisted.foreach {
      case IrHoistedNode(id, node) =>
        val hoistCtr = new Counter
        tracker ++= s"  private val $id = {\n"
        val v = emitStaticElementForHoist(node, tracker, "    ", hoistCtr)
        tracker ++= s"    $v\n"
        tracker ++= "  }\n\n"
    }

  /** Emits a [[IrNode.IrStaticElement]] without hydration checks.
    *
    * Hoisted vals are created once at object initialisation, not inside
    * `apply()`, so `Hydrating.isActive` must not be referenced.
    */
  private def emitStaticElementForHoist(
    node:   IrNode.IrStaticElement,
    buf:    LineTracker,
    indent: String,
    ctr:    Counter
  ): String =
    val v       = ctr.nextEl()
    val childNs = resolveNs(node.tag, node.ns, "")
    if childNs == "svg" then
      buf ++= s"""${ indent }val $v = dom.document.createElementNS("$SvgNs", "${ node.tag }")\n"""
    else if childNs == "math" then
      buf ++= s"""${ indent }val $v = dom.document.createElementNS("$MathNs", "${ node.tag }")\n"""
    else buf ++= s"""${ indent }val $v = dom.document.createElement("${ node.tag }")\n"""
    buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
    node.attrs.foreach { attr =>
      attr match
        case IrAttr.StaticAttr("class", value) =>
          value.split("\\s+").filter(_.nonEmpty).foreach { cls =>
            buf ++= s"""${ indent }$v.classList.add("${ escapeStr(cls) }")\n"""
          }
        case IrAttr.StaticAttr(name, value) =>
          buf ++= s"""${ indent }$v.setAttribute("$name", "${ escapeStr(value) }")\n"""
        case IrAttr.BooleanAttr(name) =>
          buf ++= s"""${ indent }$v.setAttribute("$name", "")\n"""
        case _ => () // no dynamic attrs in IrStaticElement
    }
    node.children.foreach { child =>
      child match
        case IrNode.IrStaticText(content) if !content.isBlank =>
          val tv      = ctr.nextTxt()
          val escaped = escapeStr(content)
          buf ++= s"""${ indent }val $tv = dom.document.createTextNode("$escaped")\n"""
          buf ++= s"${ indent }$v.appendChild($tv)\n"
        case childEl: IrNode.IrStaticElement =>
          val cv = emitStaticElementForHoist(childEl, buf, indent, ctr)
          buf ++= s"${ indent }$v.appendChild($cv)\n"
        case _ => () // blank IrStaticText or unexpected (won't happen in IrStaticElement)
    }
    v
