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
  *
  * This generator is selected when `MeltCompiler.compile` receives
  * `CompileMode.SPA` (the default, for backwards compatibility). For the
  * JVM / SSR code generator see [[SsrCodeGen]].
  */
object SpaCodeGen extends CodeGen:

  /** Generates a scope ID from the component name and file path (deterministic DJB2 hash).
    *
    * Uses a DJB2-variant hash (same family as Svelte 5) over the combined
    * `filePath:objectName` key so that same-named components in different
    * directories get distinct scope IDs. The 32-bit output space (~4.3 billion)
    * gives a 50% collision probability only after ~77,000 components.
    */
  def scopeIdFor(objectName: String, filePath: String = ""): String =
    val key  = if filePath.nonEmpty then s"$filePath:$objectName" else objectName
    val hash = key.foldLeft(5381L)((h, c) => ((h << 5) - h) ^ c.toLong)
    f"melt-${ hash & 0xffffffffL }%08x"

  /** Compiles a [[meltc.ast.MeltFile]] into a Scala source string. */
  def generate(
    ast:        meltc.ast.MeltFile,
    objectName: String,
    pkg:        String,
    scopeId:    String,
    hydration:  Boolean = false
  ): String =
    val buf = new StringBuilder
    val ctr = new Counter

    if pkg.nonEmpty then buf ++= s"package $pkg\n\n"

    buf ++= "import scala.language.implicitConversions\n"
    buf ++= "import scala.scalajs.js.annotation.JSExportTopLevel\n"
    buf ++= "import org.scalajs.dom\n"
    buf ++= "import melt.runtime.{ Bind, Cleanup, Mount, Ref, Style, Var, Signal }\n"
    buf ++= "import melt.runtime.*\n"
    if hydration && ast.script.flatMap(_.propsType).isDefined then
      buf ++= "import melt.runtime.json.{ PropsCodec, SimpleJson }\n"
    buf ++= "import melt.runtime.transition.*\n"
    buf ++= "import melt.runtime.animate.*\n\n"

    buf ++= s"object $objectName {\n\n"
    buf ++= s"""  private val _scopeId = "$scopeId"\n\n"""

    ast.style.foreach { s =>
      val scoped = CssScoper.scope(s.css, scopeId)
      val css    = escapeString(scoped)
      buf ++= s"""  private val _css =\n    "$css"\n\n"""
    }

    val propsType = ast.script.flatMap(_.propsType)
    ast.script.foreach { sc =>
      if sc.code.nonEmpty then
        propsType match
          case Some(typeName) =>
            val (propsDef, _) = splitPropsDefinition(sc.code, typeName)
            if propsDef.nonEmpty then
              propsDef.linesIterator.foreach(line => buf ++= s"  $line\n")
              buf += '\n'
          case None =>
    }

    // When the user chose a type name other than "Props", generate a
    // `val Props = BaseName` value alias so that call sites can always
    // use `ComponentName.Props(...)` regardless of the actual type name.
    // The companion-object alias also provides a `type Props` for annotations.
    propsType.foreach { typeName =>
      val baseName = extractBaseName(typeName)
      if baseName != "Props" then
        val tp = extractTypeParams(typeName)
        buf ++= s"  val Props = $baseName\n"
        if tp.nonEmpty then buf ++= s"  type Props$tp = $typeName\n"
        else buf ++= s"  type Props = $baseName\n"
        buf += '\n'
    }

    if hydration then
      propsType.foreach { tpe =>
        if extractTypeParams(tpe).isEmpty then // skip for generic — PropsCodec.derived doesn't support type params
          buf ++= s"  private val _propsCodec: PropsCodec[$tpe] = PropsCodec.derived\n\n"
      }

    propsType match
      case Some(typeName) =>
        val tp = extractTypeParams(typeName)
        buf ++= s"  def apply$tp(props: $typeName): dom.Element = {\n"
      case None =>
        buf ++= "  def apply(): dom.Element = {\n"

    buf ++= "    val (_result, _owner) = Owner.withNew {\n"

    if ast.style.isDefined then buf ++= "    Style.inject(_scopeId, _css)\n"

    ast.script.foreach { sc =>
      if sc.code.nonEmpty then
        val bodyCode = propsType match
          case Some(typeName) => splitPropsDefinition(sc.code, typeName)._2
          case None           => sc.code
        if bodyCode.trim.nonEmpty then
          bodyCode.linesIterator.foreach(line => buf ++= s"    $line\n")
          buf += '\n'
    }

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

    propsType match
      case Some(typeName) =>
        val tp = extractTypeParams(typeName)
        buf ++= s"  def mount$tp(target: dom.Element, props: $typeName): Unit = Mount(target, apply(props))\n\n"
      case None =>
        buf ++= "  def mount(target: dom.Element): Unit = Mount(target, apply())\n\n"

    if hydration && propsType.forall(extractTypeParams(_).isEmpty) then
      emitHydrationEntry(buf, objectName, propsType, ast)

    buf ++= "}\n"
    buf.toString

  private def emitHydrationEntry(
    buf:        StringBuilder,
    objectName: String,
    propsType:  Option[String],
    ast:        meltc.ast.MeltFile
  ): Unit =
    val moduleId      = kebabCase(objectName)
    val propsDefaults =
      propsType match
        case None           => true
        case Some(typeName) =>
          ast.script
            .map { sc =>
              val (def1, _) = splitPropsDefinition(sc.code, typeName)
              allPropsHaveDefaults(def1)
            }
            .getOrElse(true)

    val mountExpr: String = propsType match
      case None    => "Mount(host, apply())"
      case Some(_) => "Mount(host, apply(_props))"

    val resolveProps: String = propsType match
      case None      => ""
      case Some(tpe) =>
        val fallback =
          if propsDefaults then s"""            dom.console.warn(
               |              "[melt] hydrate($moduleId): no <script data-melt-props=\\\"$moduleId\\\"> " +
               |              "payload found, falling back to $tpe() defaults."
               |            )
               |            $tpe()""".stripMargin
          else s"""            dom.console.warn(
               |              "[melt] hydrate($moduleId) skipped: no <script data-melt-props=\\\"$moduleId\\\"> " +
               |              "payload found and $tpe has required fields."
               |            )
               |            null""".stripMargin
        s"""
           |    val _props: $tpe =
           |      val _tag = dom.document.querySelector("script[data-melt-props=\\\"$moduleId\\\"]")
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

    val (guardOpen, guardClose) = propsType match
      case Some(_) if !propsDefaults =>
        ("    if _props != null then\n", "")
      case _ =>
        ("", "")

    val loopIndent =
      if propsType.isDefined && !propsDefaults then "      " else "    "

    val loopBody =
      s"""${ loopIndent }starts.zip(ends).foreach { case (startNode, endNode) =>
         |${ loopIndent }  val parent = startNode.parentNode
         |${ loopIndent }  var n = startNode.nextSibling
         |${ loopIndent }  while n != null && !(n eq endNode) do
         |${ loopIndent }    val next = n.nextSibling
         |${ loopIndent }    parent.removeChild(n)
         |${ loopIndent }    n = next
         |${ loopIndent }  val host = dom.document.createElement("div")
         |${ loopIndent }  parent.insertBefore(host, endNode)
         |${ loopIndent }  $mountExpr
         |${ loopIndent }}""".stripMargin

    buf ++= s"""  /** Hydration entry exported as `$moduleId.js` via the
                |    * sbt/Scala.js asset pipeline. Locates the
                |    * `<!--[melt:$moduleId-->` / `<!--]melt:$moduleId-->`
                |    * comment markers in the document and mounts a fresh
                |    * component at each pair, using Props decoded from
                |    * the inline `<script data-melt-props="$moduleId">`
                |    * payload when present.
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

  private def emitNode(
    buf:       StringBuilder,
    node:      TemplateNode,
    indent:    String,
    ctr:       Counter,
    isRoot:    Boolean,
    parentVar: Option[String],
    ns:        String = ""
  ): String =
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        val v       = ctr.nextEl()
        val childNs =
          if tag == "svg" || (ns == "svg" && KnownSvgTags.contains(tag)) then "svg"
          else if tag == "math" || (ns == "math" && KnownMathTags.contains(tag)) then "math"
          else ns
        if childNs == "svg" then buf ++= s"""${ indent }val $v = dom.document.createElementNS("$SvgNs", "$tag")\n"""
        else if childNs == "math" then
          buf ++= s"""${ indent }val $v = dom.document.createElementNS("$MathNs", "$tag")\n"""
        else buf ++= s"""${ indent }val $v = dom.document.createElement("$tag")\n"""
        buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        // For <select bind:value>, the Bind call must come *after* <option> children are
        // appended so that the initial select.value assignment finds a matching option.
        // Collect the expression here and emit it below, after the children loop.
        val deferredSelectBind: Option[(String, Boolean)] =
          if tag.equalsIgnoreCase("select") then
            val isMultiple = attrs.exists {
              case Attr.BooleanAttr("multiple") => true
              case Attr.Static("multiple", _)   => true
              case _                            => false
            }
            attrs.collectFirst {
              case Attr.Directive("bind", "value", Some(expr), _) => (expr, isMultiple)
            }
          else None
        attrs.foreach { attr =>
          attr match
            case Attr.Directive("bind", "value", _, _) if deferredSelectBind.isDefined =>
              () // emitted after children
            case _ =>
              emitAttr(buf, v, attr, tag, indent, attrs)
        }
        children.foreach { child =>
          val cv = emitNode(buf, child, indent, ctr, isRoot = false, parentVar = Some(v), ns = childNs)
          if cv.nonEmpty then buf ++= s"${ indent }$v.appendChild($cv)\n"
        }
        deferredSelectBind.foreach {
          case (expr, isMultiple) =>
            if isMultiple then buf ++= s"${ indent }Bind.selectMultipleValue($v.asInstanceOf[dom.html.Select], $expr)\n"
            else buf ++= s"${ indent }Bind.selectValue($v.asInstanceOf[dom.html.Select], $expr)\n"
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
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            val dotMap    = code.lastIndexOf(".map(")
            val rawSource = code.substring(0, dotMap).trim
            val source    =
              if rawSource.endsWith(".value") then rawSource.dropRight(6)
              else if rawSource.endsWith(".now()") then rawSource.dropRight(6)
              else rawSource
            val fnBody = code.substring(dotMap + 5, code.length - 1).trim
            buf ++= s"${ indent }Bind.list($source, $fnBody, $anchor)\n"
            ""

          case ExprKind.KeyedMap =>
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            val keyedIdx   = code.indexOf(".keyed(")
            val source     = code.substring(0, keyedIdx).trim
            val afterKeyed = code.substring(keyedIdx + 7)
            val keyEnd     = findBalancedParen(afterKeyed, 0)
            val keyFn      = afterKeyed.substring(0, keyEnd).trim
            val rest       = afterKeyed.substring(keyEnd + 1)
            val dotMap     = rest.indexOf(".map(")
            val fnBody     = rest.substring(dotMap + 5, rest.length - 1).trim
            buf ++= s"${ indent }Bind.each($source, $keyFn, $fnBody, $anchor)\n"
            ""

          case ExprKind.DomExpr =>
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            extractReactiveSource(code) match
              case Some(source) =>
                buf ++= s"${ indent }Bind.show($source, _ => { $code }, $anchor)\n"
              case None =>
                buf ++= s"${ indent }Bind.show(() => { $code }, $anchor)\n"
            ""

          case ExprKind.DomResult =>
            // Expression that directly creates and returns a dom.Element (e.g. Await(...) { ... }).
            val v = ctr.nextEl()
            buf ++= s"${ indent }val $v: dom.Element = {\n${ indent }  ${ code.trim }\n${ indent }}\n"
            v

          case ExprKind.FragmentResult =>
            // InlineTemplate multiple children wrapped in a DocumentFragment.
            // Typed as dom.Node since DocumentFragment is not a dom.Element.
            val v = ctr.nextEl()
            buf ++= s"${ indent }val $v: dom.Node = {\n${ indent }  ${ code.trim }\n${ indent }}\n"
            v

          case ExprKind.TrustedHtmlExpr =>
            // Anchor-based raw HTML insertion — no wrapper element, matching Svelte 5's {@html}.
            // A comment node acts as the anchor; Bind.htmlAnchor inserts parsed HTML nodes
            // immediately before it and replaces them on reactive updates.
            val anchor = ctr.nextTxt()
            buf ++= s"""${ indent }val $anchor = dom.document.createComment("melt-html")\n"""
            parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
            extractReactiveSource(code) match
              case Some(source) =>
                buf ++= s"${ indent }Bind.htmlAnchor($source, _ => { $code }, $anchor)\n"
              case None =>
                buf ++= s"${ indent }Bind.htmlAnchor($code, $anchor)\n"
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
                innerBuf ++= s"${ indent }  val _frag = dom.document.createDocumentFragment()\n"
                multiple.foreach { n =>
                  val v =
                    emitNode(innerBuf, n, indent + "  ", innerCtr, isRoot = false, parentVar = Some("_frag"))
                  if v.nonEmpty then innerBuf ++= s"${ indent }  _frag.appendChild($v)\n"
                }
                exprBuf ++= s"{\n$innerBuf${ indent }  _frag\n${ indent }}"
        }
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

      case TemplateNode.Document(attrs) =>
        attrs.foreach {
          case Attr.EventHandler(event, expr) =>
            buf ++= s"""${ indent }Document.on("$event")($expr)\n"""
          case Attr.Directive("bind", prop, Some(expr), _) =>
            val method = prop match
              case "visibilityState"    => "bindVisibilityState"
              case "fullscreenElement"  => "bindFullscreenElement"
              case "pointerLockElement" => "bindPointerLockElement"
              case "activeElement"      => "bindActiveElement"
              case other                => s"bind${ other.capitalize }"
            buf ++= s"${ indent }Document.$method($expr)\n"
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
        attrs.foreach(emitAttr(setupBuf, elVar, _, "", s"$indent  ", attrs))
        children.foreach { child =>
          val cv = emitNode(setupBuf, child, s"$indent  ", ctr, isRoot = false, parentVar = Some(elVar))
          if cv.nonEmpty then setupBuf ++= s"$indent  $elVar.appendChild($cv)\n"
        }
        buf ++= s"""${ indent }val $anchor = dom.document.createComment("")\n"""
        parentVar.foreach(p => buf ++= s"${ indent }$p.appendChild($anchor)\n")
        buf ++= s"${ indent }Bind.dynamicElement($tagExpr, $anchor, _scopeId, ($elVar: dom.Element) => {\n"
        buf ++= setupBuf.toString
        buf ++= s"${ indent }  ()\n"
        buf ++= s"${ indent }})\n"
        ""

      case TemplateNode.Boundary(attrs, children, pending, failed) =>
        val idx   = ctr.nextChildIdx()
        val inner = indent + "  "

        // ── pending lambda ──────────────────────────────────────────────────
        val pendingProp: String = pending match
          case None                          => ""
          case Some(PendingBlock(pChildren)) =>
            val pVar = s"_bPending$idx"
            buf ++= s"${ indent }val $pVar: (() => dom.Element) = () => {\n"
            emitBoundaryBody(buf, pChildren, inner, ctr)
            buf ++= s"${ indent }}\n"
            s", pending = Some($pVar)"

        // ── fallback lambda ─────────────────────────────────────────────────
        val fallbackProp: String = failed match
          case None                                             => ""
          case Some(FailedBlock(errorVar, resetVar, fChildren)) =>
            val fVar = s"_bFallback$idx"
            buf ++= s"${ indent }val $fVar: (Throwable, () => Unit) => dom.Element = ($errorVar, $resetVar) => {\n"
            emitBoundaryBody(buf, fChildren, inner, ctr)
            buf ++= s"${ indent }}\n"
            s", fallback = $fVar"

        // ── onError prop ────────────────────────────────────────────────────
        val onErrorProp: String = attrs
          .collectFirst {
            case Attr.EventHandler("error", expr) => s", onError = $expr"
          }
          .getOrElse("")

        // ── children lambda ─────────────────────────────────────────────────
        val cVar = s"_bChildren$idx"
        buf ++= s"${ indent }val $cVar: (() => dom.Element) = () => {\n"
        emitBoundaryBody(buf, children, inner, ctr)
        buf ++= s"${ indent }}\n"

        // ── Boundary.create call ────────────────────────────────────────────
        val fragVar = s"_bFrag$idx"
        buf ++= s"${ indent }val $fragVar = Boundary.create(Boundary.Props(children = $cVar$pendingProp$fallbackProp$onErrorProp))\n"

        // ── append or wrap ──────────────────────────────────────────────────
        parentVar match
          case Some(parent) =>
            buf ++= s"${ indent }$parent.appendChild($fragVar)\n"
            ""
          case None =>
            val wVar = s"_bWrap$idx"
            buf ++= s"${ indent }val $wVar = dom.document.createElement(\"div\")\n"
            buf ++= s"""${ indent }$wVar.setAttribute("style", "display: contents")\n"""
            buf ++= s"${ indent }$wVar.appendChild($fragVar)\n"
            wVar

      case TemplateNode.KeyBlock(keyExpr, children) =>
        val idx       = ctr.nextChildIdx()
        val inner     = indent + "  "
        val kVar      = s"_keyRender$idx"
        val startAnch = ctr.nextTxt()
        val endAnch   = ctr.nextTxt()

        // G-2 / G-5: render lambda returns DocumentFragment (no div wrapper, text exprs work)
        buf ++= s"${ indent }val $kVar: (() => dom.DocumentFragment) = () => {\n"
        emitKeyBody(buf, children, inner, ctr)
        buf ++= s"${ indent }}\n"

        buf ++= s"""${ indent }val $startAnch = dom.document.createComment("melt-key-start")\n"""
        buf ++= s"""${ indent }val $endAnch   = dom.document.createComment("melt-key-end")\n"""
        parentVar.foreach { p =>
          buf ++= s"${ indent }$p.appendChild($startAnch)\n"
          buf ++= s"${ indent }$p.appendChild($endAnch)\n"
        }
        buf ++= s"${ indent }Bind.key($keyExpr, $kVar, $startAnch, $endAnch)\n"
        ""

      case TemplateNode.SnippetDef(name, params, children) =>
        emitSnippetDef(buf, name, params, children, indent, ctr)
        "" // snippets define a val, they don't produce an inline element reference

      case TemplateNode.RenderCall(expr) =>
        val v = ctr.nextEl()
        buf ++= s"${ indent }val $v = $expr\n"
        v

      case TemplateNode.Component(name, attrs, children) =>
        val v = ctr.nextEl()

        val spreadExpr = attrs.collectFirst { case Attr.Spread(expr) => expr }

        val hasStyled = attrs.exists {
          case Attr.BooleanAttr("styled") => true
          case _                          => false
        }

        val bindThisExpr = attrs.collectFirst {
          case Attr.Directive("bind", "this", Some(expr), _) => expr
        }

        val filteredChildren = children.filter {
          case TemplateNode.Text(t) => !t.isBlank
          case _                    => true
        }

        // Separate snippet definitions from regular children
        val (snippetChildren, regularChildren) = filteredChildren.partition {
          case TemplateNode.SnippetDef(_, _, _) => true
          case _                                => false
        }

        // Emit snippet lambdas and collect their prop assignments
        val snippetArgs = snippetChildren.collect {
          case TemplateNode.SnippetDef(snName, snParams, snChildren) =>
            val varName = s"_snippet_${ snName }_${ ctr.nextChildIdx() }"
            emitSnippetDef(buf, varName, snParams, snChildren, indent, ctr)
            s"$snName = $varName"
        }

        spreadExpr match
          case Some(expr) =>
            buf ++= s"${ indent }val $v = $name($expr)\n"
          case None =>
            val baseArgs  = buildPropsArgs(attrs, regularChildren, indent, ctr, buf)
            val allArgs   = (if baseArgs.nonEmpty then List(baseArgs) else Nil) ++ snippetArgs
            val propsArgs = allArgs.mkString(", ")
            if propsArgs.nonEmpty then buf ++= s"${ indent }val $v = $name($name.Props($propsArgs))\n"
            else buf ++= s"${ indent }val $v = $name()\n"

        if hasStyled then buf ++= s"${ indent }$v.classList.add(_scopeId)\n"
        bindThisExpr.foreach { expr =>
          buf ++= s"${ indent }$expr.set($v)\n"
        }
        v

  private def emitAttr(
    buf:      StringBuilder,
    v:        String,
    attr:     Attr,
    tag:      String,
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
        buf ++= s"""${ indent }Bind.cls($v, $expr)\n"""
      case Attr.Dynamic(name, expr) if SpaCodeGen.htmlBooleanAttrs.contains(name) =>
        buf ++= s"""${ indent }Bind.booleanAttr($v, "$name", $expr)\n"""
      case Attr.Dynamic(name, expr) =>
        buf ++= s"""${ indent }Bind.attr($v, "$name", $expr)\n"""
      case Attr.EventHandler(event, expr) =>
        buf ++= s"""${ indent }$v.addEventListener("$event", $expr)\n"""
      case Attr.Directive("on", event, Some(expr), mods) =>
        if mods.contains("preventDefault") then
          buf ++= s"""${ indent }$v.addEventListener("$event", ((e: dom.Event) => { e.preventDefault(); ($expr).asInstanceOf[Any] }))\n"""
        else buf ++= s"""${ indent }$v.addEventListener("$event", ((_: dom.Event) => ($expr).asInstanceOf[Any]))\n"""
      case Attr.Directive("bind", "value", Some(expr), _) =>
        tag.toLowerCase match
          case "textarea" =>
            buf ++= s"""${ indent }Bind.textareaValue($v.asInstanceOf[dom.html.TextArea], $expr)\n"""
          case "select" =>
            () // handled after children in emitNode (deferredSelectBind)
          case _ =>
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
      case Attr.Directive("bind", "clientWidth", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.clientWidth($v, $expr)\n"""
      case Attr.Directive("bind", "clientHeight", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.clientHeight($v, $expr)\n"""
      case Attr.Directive("bind", "offsetWidth", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.offsetWidth($v, $expr)\n"""
      case Attr.Directive("bind", "offsetHeight", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.offsetHeight($v, $expr)\n"""
      case Attr.Directive("bind", "currentTime", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaCurrentTime($v, $expr)\n"""
      case Attr.Directive("bind", "duration", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaDuration($v, $expr)\n"""
      case Attr.Directive("bind", "paused", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaPaused($v, $expr)\n"""
      case Attr.Directive("bind", "volume", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaVolume($v, $expr)\n"""
      case Attr.Directive("bind", "muted", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaMuted($v, $expr)\n"""
      case Attr.Directive("bind", "playbackRate", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaPlaybackRate($v, $expr)\n"""
      case Attr.Directive("bind", "seeking", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaSeeking($v, $expr)\n"""
      case Attr.Directive("bind", "ended", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaEnded($v, $expr)\n"""
      case Attr.Directive("bind", "readyState", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaReadyState($v, $expr)\n"""
      case Attr.Directive("bind", "videoWidth", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaVideoWidth($v, $expr)\n"""
      case Attr.Directive("bind", "videoHeight", Some(expr), _) =>
        buf ++= s"""${ indent }Bind.mediaVideoHeight($v, $expr)\n"""
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
        buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltAnimateFn")(($fn: AnimateFn).asInstanceOf[scalajs.js.Any])\n"""
        buf ++= s"""${ indent }$v.asInstanceOf[scalajs.js.Dynamic].updateDynamic("_meltAnimateParams")(($params: AnimateParams).asInstanceOf[scalajs.js.Any])\n"""
      case Attr.Spread(expr) =>
        buf ++= s"""${ indent }$expr.apply($v)\n"""
      case Attr.Directive(_, _, _, _) | Attr.Shorthand(_) =>

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
        args += s"on${ event.capitalize } = $expr"
      case Attr.BooleanAttr("styled") =>
      case Attr.BooleanAttr(name)     =>
        args += s"$name = true"
      case _ =>
    }

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
        buf ++= s"${ inner }val _frag = dom.document.createDocumentFragment()\n"
        multiple.foreach { child =>
          val cv = emitNode(buf, child, inner, childCtr, isRoot = false, parentVar = Some("_frag"))
          if cv.nonEmpty then buf ++= s"${ inner }_frag.appendChild($cv)\n"
        }
        buf ++= s"${ inner }_frag\n"

    buf ++= s"${ indent }}\n"
    varName

  /** Emits a snippet definition as a typed Scala lambda val.
    *
    * `{#snippet render(todo: Todo)}` emits:
    * {{{
    * val render: (Todo) => dom.Element = (todo: Todo) => {
    *   // ... body ...
    * }
    * }}}
    */
  private def emitSnippetDef(
    buf:      StringBuilder,
    name:     String,
    params:   List[SnippetParam],
    children: List[TemplateNode],
    indent:   String,
    ctr:      Counter
  ): Unit =
    val inner    = indent + "  "
    val innerCtr = new Counter

    // Build type annotation and parameter list
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

    val filteredChildren = children.filter {
      case TemplateNode.Text(t) => !t.isBlank
      case _                    => true
    }
    filteredChildren match
      case Nil =>
        buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case single :: Nil =>
        val cv = emitNode(buf, single, inner, innerCtr, isRoot = false, parentVar = None)
        if cv.nonEmpty then buf ++= s"${ inner }$cv\n"
        else buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case multiple =>
        buf ++= s"${ inner }val _frag = dom.document.createDocumentFragment()\n"
        multiple.foreach { child =>
          val cv = emitNode(buf, child, inner, innerCtr, isRoot = false, parentVar = Some("_frag"))
          if cv.nonEmpty then buf ++= s"${ inner }_frag.appendChild($cv)\n"
        }
        buf ++= s"${ inner }_frag\n"

    buf ++= s"${ indent }}\n"

  /** Emits the body of a key-block render lambda.
    *
    * Appends all children to a [[dom.DocumentFragment]] (`_kFrag`) so that:
    *   - No wrapper `<div>` appears in the DOM (Svelte 5 fragment semantics).
    *   - Text expressions use `Bind.text(v, _kFrag)` which is both reactive and
    *     correctly typed (no `dom.Text` vs `dom.Element` mismatch).
    *   - Each direct child element retains its own `in:` / `out:` transitions,
    *     allowing [[melt.runtime.Bind.key]] to play them individually.
    *
    * Does NOT emit the surrounding `val x = () => {` / `}` — callers do that.
    */
  private def emitKeyBody(
    buf:      StringBuilder,
    children: List[TemplateNode],
    inner:    String,
    ctr:      Counter
  ): Unit =
    buf ++= s"${ inner }val _kFrag = dom.document.createDocumentFragment()\n"
    children.foreach { child =>
      val cv = emitNode(buf, child, inner, ctr, isRoot = false, parentVar = Some("_kFrag"))
      if cv.nonEmpty then buf ++= s"${ inner }_kFrag.appendChild($cv)\n"
    }
    buf ++= s"${ inner }_kFrag\n"

  /** Emits the body of a boundary lambda (pending / fallback / children).
    * Writes to `buf` and leaves the cursor at the end of the last statement.
    * Does NOT emit the surrounding `val x = () => {` / `}` — callers do that.
    */
  private def emitBoundaryBody(
    buf:      StringBuilder,
    children: List[TemplateNode],
    inner:    String,
    ctr:      Counter
  ): Unit =
    children match
      case Nil =>
        buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case single :: Nil =>
        val cv = emitNode(buf, single, inner, ctr, isRoot = false, parentVar = None)
        if cv.nonEmpty then buf ++= s"${ inner }$cv\n"
        else buf ++= s"${ inner }dom.document.createElement(\"span\")\n"
      case multiple =>
        buf ++= s"${ inner }val _bFrag = dom.document.createElement(\"div\")\n"
        multiple.foreach { child =>
          val cv = emitNode(buf, child, inner, ctr, isRoot = false, parentVar = Some("_bFrag"))
          if cv.nonEmpty then buf ++= s"${ inner }_bFrag.appendChild($cv)\n"
        }
        buf ++= s"${ inner }_bFrag\n"

  /** Splits script code into (propsDefinition, bodyCode).
    * The props definition (e.g. `case class Props(...)`) goes at object level;
    * everything else goes inside `create()`.
    */
  /** Converts `objectName` to kebab-case for Vite `moduleID`.
    * `Counter` → `counter`, `TodoList` → `todo-list`.
    */
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

  /** Returns the Scala code snippet that mounts a fresh component at
    * the `host` element created by `_meltHydrateEntry`.
    *
    * Components with `Props` call `apply(<TypeName>())` — requiring the
    * user's case class to have default values for every field. Components
    * without Props simply call `apply()`.
    */
  private def hydrationApplyCall(propsType: Option[String]): String =
    propsType match
      case Some(tpe) => s"Mount(host, apply($tpe()))"
      case None      => "Mount(host, apply())"

  /** Heuristic check: does every field of the (single) Props case class
    * have a default value? Used by `_meltHydrateEntry` to decide whether
    * it can emit a working hydration body (all defaults) or merely a
    * stub that warns at runtime.
    *
    * Accepts both `case class Props()` (trivially true — no params) and
    * full signatures like
    * `case class Props(a: Int = 0, b: String = "x")`. Nested
    * generics / function types / default expressions containing commas
    * are handled via a simple bracket-depth tracker in
    * [[splitTopLevelCommas]].
    *
    * The check is intentionally lenient: it errs towards "defaultable"
    * whenever it cannot parse the declaration (e.g. multi-line Props
    * definitions), because a false positive only causes a compile
    * error at the `Props()` call site — which the developer can then
    * fix by adding defaults or switching to the stub path manually.
    */
  private def allPropsHaveDefaults(propsDef: String): Boolean =
    val open  = propsDef.indexOf('(')
    val close = propsDef.lastIndexOf(')')
    if open < 0 || close <= open then true
    else
      val params = propsDef.substring(open + 1, close).trim
      if params.isEmpty then true
      else splitTopLevelCommas(params).forall(_.contains(" = "))

  /** Splits `s` by commas that are not nested inside `(`, `[`, or `{`. */
  private def splitTopLevelCommas(s: String): List[String] =
    val buf    = scala.collection.mutable.ListBuffer.empty[String]
    val curBuf = new StringBuilder
    var depth  = 0
    var i      = 0
    while i < s.length do
      val c = s.charAt(i)
      c match
        case '(' | '[' | '{' =>
          depth += 1
          curBuf += c
        case ')' | ']' | '}' =>
          depth -= 1
          curBuf += c
        case ',' if depth == 0 =>
          buf += curBuf.toString.trim
          curBuf.clear()
        case _ =>
          curBuf += c
      i += 1
    if curBuf.nonEmpty then buf += curBuf.toString.trim
    buf.toList

  /** Splits the user's script into (hoistedTypeDecls, restBody).
    *
    * Extracts every top-level type declaration (`case class`, `type`,
    * `sealed trait`, `sealed abstract class`, `enum`) so that Props and
    * any types it references live at the object level. Non-type lines
    * (vals, side-effects) stay in the per-instance body.
    *
    * The `propsTypeName` parameter is retained for backwards
    * compatibility with the old call sites but is no longer used —
    * the extractor is type-agnostic.
    */
  /** Extracts the base (un-parameterised) type name from a type name.
    * `"Props[T]"` → `"Props"`, `"Todo[K, V]"` → `"Todo"`, `"Props"` → `"Props"`.
    */
  private def extractBaseName(typeName: String): String =
    val i = typeName.indexOf('[')
    if i < 0 then typeName else typeName.substring(0, i)

  /** Extracts the balanced type-parameter bracket from a type name.
    * `"Props[T]"` → `"[T]"`, `"Props[K, V <: Ordered[V]]"` → `"[K, V <: Ordered[V]]"`.
    * Returns `""` if the type has no type parameters.
    */
  private def extractTypeParams(typeName: String): String =
    val i = typeName.indexOf('[')
    if i < 0 then ""
    else
      var depth = 0
      var j     = i
      var found = false
      while j < typeName.length && !found do
        typeName(j) match
          case '[' => depth += 1
          case ']' =>
            depth -= 1
            if depth == 0 then found = true
          case _ => ()
        if !found then j += 1
      if found then typeName.substring(i, j + 1) else ""

  private def splitPropsDefinition(code: String, propsTypeName: String): (String, String) =
    val (typeDecls, rest) = splitTypeDecls(code)
    (typeDecls.mkString("\n\n"), rest)

  /** Heuristic: extract all top-level type declarations from `script`.
    * Returns the list of declarations (in source order) and the
    * remaining non-type body. Same logic as `SsrCodeGen.splitTypeDecls`,
    * kept local because meltc's codegen package doesn't share helpers
    * across files.
    */
  private def splitTypeDecls(script: String): (List[String], String) =
    if script.isEmpty then (Nil, script)
    else
      val lines     = script.linesIterator.toVector
      val typeDecls = scala.collection.mutable.ListBuffer.empty[String]
      val rest      = scala.collection.mutable.ListBuffer.empty[String]
      var i         = 0
      while i < lines.length do
        val line    = lines(i)
        val trimmed = line.trim
        if isTypeDeclStart(trimmed) then
          val (endIdx, chunk) = collectBalanced(lines, i)
          typeDecls += chunk.mkString("\n")
          i = endIdx + 1
        else
          rest += line
          i += 1
      (typeDecls.toList, rest.mkString("\n"))

  private def isTypeDeclStart(trimmed: String): Boolean =
    trimmed.startsWith("case class ") ||
      trimmed.startsWith("type ") ||
      trimmed.startsWith("sealed trait ") ||
      trimmed.startsWith("sealed abstract class ") ||
      trimmed.startsWith("enum ")

  private def collectBalanced(
    lines: Vector[String],
    start: Int
  ): (Int, Vector[String]) =
    var depth       = 0
    var seenAnyOpen = false
    val buf         = scala.collection.mutable.ListBuffer.empty[String]
    var i           = start
    var done        = false
    while !done && i < lines.length do
      val line = lines(i)
      buf += line
      line.foreach {
        case '(' | '[' | '{' =>
          depth += 1
          seenAnyOpen = true
        case ')' | ']' | '}' =>
          depth -= 1
        case _ => ()
      }
      if (seenAnyOpen && depth == 0) || (!seenAnyOpen && depth == 0) then done = true
      i += 1
    (i - 1, buf.toVector)

  private enum ExprKind:
    case ListMap
    case KeyedMap
    case DomExpr
    case DomResult      // single dom.Element (e.g. Await, explicit createElement)
    case FragmentResult // dom.DocumentFragment (InlineTemplate multiple children)
    case TrustedHtmlExpr
    case PlainText

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

  /** Returns true when the code directly creates a DOM element (e.g. via `Await` or an explicit
    * `createElement` call) without being a list-map or conditional expression.
    * Used to classify expressions such as `{Await(future) { case ... }}` that are expanded
    * from [[TemplateNode.InlineTemplate]] and produce a `dom.Element` rather than a plain value.
    */
  private def returnsDomElementDirectly(code: String): Boolean =
    code.contains("createElement") || code.contains("createElementNS")

  private def classifyExpr(code: String): ExprKind =
    val trimmed = code.trim
    if trimmed.contains(".keyed(") && trimmed.contains(".map(") then ExprKind.KeyedMap
    else if trimmed.contains(".map(") then
      val dotMap  = trimmed.lastIndexOf(".map(")
      val mapBody = trimmed.substring(dotMap + 5)
      if containsDomConstruction(mapBody) then ExprKind.ListMap else ExprKind.PlainText
    else if trimmed.contains("TrustedHtml") then ExprKind.TrustedHtmlExpr
    else if (trimmed.startsWith("if ") || trimmed.startsWith("if(")) && containsDomConstruction(trimmed) then
      ExprKind.DomExpr
    else if trimmed.contains(" match") && containsDomConstruction(trimmed) then ExprKind.DomExpr
    else if trimmed.contains("createDocumentFragment") then ExprKind.FragmentResult
    else if returnsDomElementDirectly(trimmed) then ExprKind.DomResult
    else ExprKind.PlainText

  /** Attempts to extract a reactive source (Var or Signal identifier) from a conditional
    * DOM expression so the reactive `Bind.show(source, render, anchor)` overload can be used.
    *
    * Recognized patterns:
    *   - `if <ident> then ...`             → `Some("<ident>")`
    *   - `if !<ident> then ...`            → `Some("<ident>")`
    *   - `if <ident>.now() then ...`       → `Some("<ident>")` (legacy)
    *   - `if !<ident>.now() then ...`      → `Some("<ident>")` (legacy)
    *   - `<ident> match { ... }` (match on a Var/Signal via .now()) → extracted identifier
    *
    * Returns `None` if the expression cannot be mapped to a single reactive source.
    */
  private def extractReactiveSource(code: String): Option[String] =
    val trimmed = code.trim
    // Match `ident.value`, `ident.now()` (legacy), and bare `ident` (implicit conversion)
    val ifValueRe = """^if\s+!?([a-zA-Z_][a-zA-Z0-9_.]*)\.(?:value|now\(\))""".r
    val ifBareRe  = """^if\s+!?([a-zA-Z_][a-zA-Z0-9_.]*)\s+then\b""".r
    ifValueRe
      .findFirstMatchIn(trimmed)
      .map(_.group(1))
      .orElse(ifBareRe.findFirstMatchIn(trimmed).map(_.group(1)))

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
