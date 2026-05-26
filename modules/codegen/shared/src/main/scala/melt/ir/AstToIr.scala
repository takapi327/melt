/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir

import melt.analysis.ScalaTextUtils
import melt.ast.*
import melt.codegen.{ CssScoper, ReactiveInferencer, SpaCodeGen }
import melt.NodePositions

/** Lowers a [[MeltFile]] AST into an [[IrComponent]].
  *
  * This is where semantic classification (previously `SpaCodeGen.classifyExpr`)
  * happens. The resulting IR nodes carry their semantic meaning as types,
  * so the downstream [[melt.emit.SpaEmitter]] and [[melt.emit.SsrEmitter]] never
  * need to inspect string contents.
  */
object AstToIr:

  def lower(
    ast:               MeltFile,
    objectName:        String,
    pkg:               String,
    scopeId:           String,
    hydration:         Boolean = false,
    sourcePath:        String = "",
    scriptBodyLine:    Int = 1,
    templateStartLine: Int = 1,
    templateSource:    String = "",
    positions:         NodePositions = NodePositions.empty
  ): IrComponent =
    val propsType = ast.script.flatMap(_.propsType).map(buildPropsType(_, ast.script))
    // ⚠ Call splitTypeDecls once on the raw script code.
    // Calling extractScriptBody (which already strips typeDecls) and then
    // extractTypeDecls on the result would always yield an empty typeDecls list
    // (double-stripping bug).
    val rawCode                 = ast.script.map(_.code.trim).getOrElse("")
    val (typeDecls, scriptBody) = if rawCode.isEmpty then (Nil, "") else splitTypeDecls(rawCode)
    val reactiveVars            = ScalaTextUtils.extractReactiveVars(rawCode)
    val style                   = ast.style.map(s => IrStyle(CssScoper.scope(s.content, scopeId), scopeId))
    val posBuilder              = IrNodePositions.builder()
    val template                = ast.template.flatMap(
      lowerNode(_, scopeId, positions, templateSource, templateStartLine, posBuilder, reactiveVars)
    )

    IrComponent(
      objectName        = objectName,
      pkg               = pkg,
      scopeId           = scopeId,
      propsType         = propsType,
      scriptBody        = scriptBody,
      fileImports       = ast.script.toList.flatMap(_.imports),
      typeDecls         = typeDecls,
      style             = style,
      template          = template,
      hydration         = hydration,
      sourcePath        = sourcePath,
      sourceMap         = IrSourceMap.empty, // populated by Emitters during emit()
      scriptBodyLine    = scriptBodyLine,
      templateStartLine = templateStartLine,
      nodePositions     = posBuilder.build()
    )

  // ── Node lowering ─────────────────────────────────────────────────────────

  private def lowerNode(
    node:              TemplateNode,
    scopeId:           String,
    positions:         NodePositions,
    templateSource:    String,
    templateStartLine: Int,
    posBuilder:        IrNodePositions.Builder,
    reactiveVars:      Set[String] = Set.empty
  ): Option[IrNode] =
    val span = positions.spanOf(node)
    val line = span.absoluteLine(templateSource, templateStartLine)
    val col  = span.column(templateSource)

    // Helper to recurse children, threading posBuilder through.
    def lower(n: TemplateNode) =
      lowerNode(n, scopeId, positions, templateSource, templateStartLine, posBuilder, reactiveVars)

    val result = node match

      case TemplateNode.Text(content) if content.isBlank =>
        None

      case TemplateNode.Text(content) =>
        Some(IrNode.IrStaticText(content))

      case TemplateNode.Expression(code) =>
        Some(lowerExpression(code, reactiveVars))

      case TemplateNode.Element(tag, attrs, children) =>
        val irAttrs    = attrs.flatMap(lowerAttr(_, tag, attrs))
        val irChildren = children.flatMap(lower)
        val ns         = namespaceFor(tag)
        val irElem     =
          if isStatic(irAttrs, irChildren) then IrNode.IrStaticElement(tag, ns, irAttrs, irChildren, scopeId)
          else IrNode.IrElement(tag, ns, irAttrs, irChildren, scopeId)
        Some(irElem)

      case TemplateNode.Component(name, attrs, children) =>
        // `Attr.Spread` on a component bypasses the Props constructor entirely.
        // Record it in spreadExpr; lowerProp drops Spread via the catch-all `case _ => None`.
        val spreadExpr   = attrs.collectFirst { case Attr.Spread(expr) => ScalaExpr(expr) }
        val hasStyled    = attrs.exists { case Attr.BooleanAttr("styled") => true; case _ => false }
        val bindThisExpr = attrs.collectFirst { case Attr.Directive("bind", "this", Some(expr), _) => ScalaExpr(expr) }
        val props        = attrs.flatMap(lowerProp)
        val irChildren   = children.flatMap(lower)
        val childSlot    = if irChildren.nonEmpty then Some(IrChildrenSlot(irChildren)) else None
        Some(IrNode.IrComponent(name, props, childSlot, spreadExpr, hasStyled, bindThisExpr))

      case TemplateNode.InlineTemplate(parts) =>
        // Phase 1–3: keep as IrInlineTemplate so Emitters can delegate to
        // the proven existing bridge logic.
        // HTML parts are lowered recursively so Emitters can use emitNode().
        // Phase 4: expand to IrList / IrConditional instead.
        val irParts = parts.map:
          case melt.ast.InlineTemplatePart.Code(code) =>
            IrInlineTemplatePart.Code(code)
          case melt.ast.InlineTemplatePart.Html(nodes) =>
            IrInlineTemplatePart.Html(nodes.flatMap(lower))
        Some(IrNode.IrInlineTemplate(irParts))

      case TemplateNode.Head(children) =>
        Some(IrNode.IrHead(children.flatMap(lower)))

      // Window/Body/Document: use lowerWindowAttr / lowerDocumentAttr because
      // their bind: directives (scrollY, visibilityState, …) are NOT in the
      // element-level IrAttr cases and would silently vanish via lowerAttr's catch-all.
      case TemplateNode.Window(attrs)   => Some(IrNode.IrWindow(attrs.flatMap(lowerWindowAttr)))
      case TemplateNode.Body(attrs)     => Some(IrNode.IrBody(attrs.flatMap(lowerBodyAttr)))
      case TemplateNode.Document(attrs) => Some(IrNode.IrDocument(attrs.flatMap(lowerDocumentAttr)))

      case TemplateNode.DynamicElement(tagExpr, attrs, children) =>
        val irAttrs    = attrs.flatMap(lowerAttr(_, "", attrs))
        val irChildren = children.flatMap(lower)
        Some(IrNode.IrDynamicElement(ScalaExpr(tagExpr), irAttrs, irChildren, scopeId))

      case TemplateNode.Boundary(attrs, children, pending, failed) =>
        val onError    = attrs.collectFirst { case Attr.EventHandler("error", e) => ScalaExpr(e) }
        val irChildren = children.flatMap(lower)
        val irPending  = pending.map(_.children.flatMap(lower))
        val irFailed   = failed.map(f => IrFailedBlock(f.errorVar, f.resetVar, f.children.flatMap(lower)))
        Some(IrNode.IrBoundary(irChildren, irPending, irFailed, onError))

      case TemplateNode.KeyBlock(keyExpr, children) =>
        Some(IrNode.IrKeyBlock(ScalaExpr(keyExpr), children.flatMap(lower)))

      case TemplateNode.SnippetDef(name, params, children) =>
        val irParams = params.map(p => IrSnippetParam(p.name, p.typeAnnotation))
        Some(IrNode.IrSnippetDef(name, irParams, children.flatMap(lower)))

      case TemplateNode.RenderCall(expr) =>
        Some(IrNode.IrRenderCall(ScalaExpr(expr)))

    result.foreach(posBuilder.put(_, line, col))
    result

  // ── Expression classification (moved from SpaCodeGen.classifyExpr) ────────

  /** Classifies a raw Scala expression string into a typed [[IrNode]].
    *
    * This is the canonical home of the expression classification logic that was
    * previously duplicated across SpaCodeGen and SsrCodeGen. Running it once at
    * IR construction time means the Emitters receive unambiguous IR node types.
    */
  private[melt] def lowerExpression(code: String, reactiveVars: Set[String] = Set.empty): IrNode =
    val trimmed  = code.trim
    val stripped = ScalaTextUtils.stripStringLiterals(trimmed)

    if trimmed == "children" then IrNode.IrChildren
    else if isKeyedList(stripped) then buildKeyedList(trimmed)
    else if isUnkeyedList(stripped) then buildUnkeyedList(trimmed)
    else if stripped.contains("TrustedHtml") then IrNode.IrRawHtml(extractReactiveSource(trimmed), ScalaExpr(trimmed))
    else if isConditionalDom(stripped) then IrNode.IrConditional(extractReactiveSource(trimmed), ScalaExpr(trimmed))
    else if stripped.contains("createDocumentFragment") then IrNode.IrFragmentResult(ScalaExpr(trimmed))
    else if returnsDomDirectly(stripped) then IrNode.IrDomResult(ScalaExpr(trimmed))
    else
      val kind = ReactiveInferencer.infer(trimmed, reactiveVars)
      IrNode.IrDynamicText(ScalaExpr(trimmed), kind)

  private def isKeyedList(code: String): Boolean =
    code.contains(".keyed(") && code.contains(".map(")

  private def isUnkeyedList(code: String): Boolean =
    code.contains(".map(") && containsDomConstruction(afterLastDotMap(code))

  private def isConditionalDom(code: String): Boolean =
    (code.startsWith("if ") || code.startsWith("if(") || code.contains(" match")) &&
      containsDomConstruction(code)

  private def containsDomConstruction(code: String): Boolean =
    code.contains("createElement") ||
      code.contains("dom.document") ||
      code.contains(": dom.Node") ||
      code.contains(": dom.Element") ||
      code.count(_ == '\n') > 1

  private def returnsDomDirectly(code: String): Boolean =
    code.contains("createElement") || code.contains("createElementNS")

  private def afterLastDotMap(code: String): String =
    val idx = code.lastIndexOf(".map(")
    if idx < 0 then "" else code.substring(idx + 5)

  private def buildUnkeyedList(code: String): IrNode.IrList =
    val dotMap    = code.lastIndexOf(".map(")
    val rawSource = code.substring(0, dotMap).trim
    val source    = stripValueSuffix(rawSource)
    val fnBody    = code.substring(dotMap + 5, code.length - 1).trim
    IrNode.IrList(ScalaExpr(source), ScalaExpr(fnBody))

  private def buildKeyedList(code: String): IrNode.IrKeyedList =
    val keyedIdx   = code.indexOf(".keyed(")
    val source     = stripValueSuffix(code.substring(0, keyedIdx).trim)
    val afterKeyed = code.substring(keyedIdx + 7)
    val keyEnd     = findBalancedParen(afterKeyed, 0)
    val keyFn      = afterKeyed.substring(0, keyEnd).trim
    val rest       = afterKeyed.substring(keyEnd + 1)
    val dotMap     = rest.indexOf(".map(")
    val fnBody     = rest.substring(dotMap + 5, rest.length - 1).trim
    IrNode.IrKeyedList(ScalaExpr(source), ScalaExpr(keyFn), ScalaExpr(fnBody))

  private def stripValueSuffix(code: String): String =
    if code.endsWith(".value") then code.dropRight(6)
    else if code.endsWith(".now()") then code.dropRight(6)
    else code

  /** Extracts a reactive source identifier (for Bind.show overload selection). */
  private[melt] def extractReactiveSource(code: String): Option[ScalaExpr] =
    val trimmed   = code.trim
    val ifValueRe = """^if\s+!?([a-zA-Z_][a-zA-Z0-9_.]*)\.(?:value|now\(\))""".r
    val ifBareRe  = """^if\s+!?([a-zA-Z_][a-zA-Z0-9_.]*)\s+then\b""".r
    ifValueRe
      .findFirstMatchIn(trimmed)
      .map(m => ScalaExpr(m.group(1)))
      .orElse(ifBareRe.findFirstMatchIn(trimmed).map(m => ScalaExpr(m.group(1))))

  // ── Attr lowering ─────────────────────────────────────────────────────────

  /** Lowers a [[melt.ast.Attr]] into zero or more [[IrAttr]]s.
    *
    * Most cases produce exactly one IrAttr. `Attr.Directive("bind", "value", ...)` on
    * a `<select>` produces [[IrAttr.BindSelectValue]] with the `multiple` flag
    * pre-resolved so the Emitter does not need to inspect sibling attributes.
    *
    * Static class is retained as IrAttr.StaticAttr("class", v) so that downstream
    * Emitters can access the raw class string.  Each Emitter handles it differently:
    *   SsrEmitter.emitAttr: skips "class" (picked up by emitScopedClassAttr)
    *   SpaEmitter.emitAttr: splits by whitespace and calls classList.add
    * ⚠ Do NOT return None here — doing so loses the value and breaks emitScopedClassAttr in SSR.
    */
  private def lowerAttr(attr: Attr, tag: String, allAttrs: List[Attr]): Option[IrAttr] = attr match

    case Attr.Static(name, value) => Some(IrAttr.StaticAttr(name, value))
    case Attr.BooleanAttr(name)   => Some(IrAttr.BooleanAttr(name))

    // class={dynamicExpr} — full class string expression passed to Bind.cls (SPA)
    // or injected into emitScopedClassAttr (SSR).
    case Attr.Dynamic("class", expr) => Some(IrAttr.DynamicClass(ScalaExpr(expr)))

    // Boolean HTML attributes (disabled, checked, readonly, …) must use
    // Bind.booleanAttr (adds/removes the attr) not Bind.attr (sets a string).
    case Attr.Dynamic(name, expr) if htmlBooleanAttrs.contains(name) =>
      Some(IrAttr.DynamicBooleanAttr(name, ScalaExpr(expr)))
    case Attr.Dynamic(name, expr) => Some(IrAttr.DynamicAttr(name, ScalaExpr(expr)))

    case Attr.Spread(expr) => Some(IrAttr.Spread(ScalaExpr(expr)))

    // Shorthand on an element: treated as a dynamic attr in both SPA and SSR.
    // NOTE: current SpaCodeGen silently drops Shorthand on elements (existing bug);
    // this IR mapping is the correct behaviour and fixes it in Phase 2.
    case Attr.Shorthand(varName) => Some(IrAttr.DynamicAttr(varName, ScalaExpr(varName)))

    case Attr.EventHandler(event, expr) =>
      Some(IrAttr.EventHandler(event, ScalaExpr(expr)))

    case Attr.Directive("on", event, Some(expr), mods) =>
      Some(IrAttr.EventHandlerWithModifier(event, ScalaExpr(expr), mods))

    case Attr.Directive("bind", "value", Some(expr), _) =>
      tag.toLowerCase match
        case "textarea" => Some(IrAttr.BindTextareaValue(ScalaExpr(expr)))
        case "select"   =>
          val multiple = allAttrs.exists {
            case Attr.BooleanAttr("multiple") => true
            case Attr.Static("multiple", _)   => true
            case _                            => false
          }
          Some(IrAttr.BindSelectValue(ScalaExpr(expr), multiple))
        case _ => Some(IrAttr.BindInputValue(ScalaExpr(expr)))

    case Attr.Directive("bind", "value-int", Some(e), _)    => Some(IrAttr.BindInputValueInt(ScalaExpr(e)))
    case Attr.Directive("bind", "value-double", Some(e), _) => Some(IrAttr.BindInputValueDouble(ScalaExpr(e)))
    case Attr.Directive("bind", "checked", Some(e), _)      => Some(IrAttr.BindChecked(ScalaExpr(e)))
    case Attr.Directive("bind", "group", Some(e), _)        =>
      val isCheckbox = allAttrs.exists {
        case Attr.Static("type", "checkbox") => true
        case _                               => false
      }
      Some(IrAttr.BindGroup(ScalaExpr(e), isCheckbox))
    case Attr.Directive("bind", "this", Some(e), _)        => Some(IrAttr.BindThis(ScalaExpr(e)))
    case Attr.Directive("bind", "innerHTML", Some(e), _)   => Some(IrAttr.BindInnerHtml(ScalaExpr(e)))
    case Attr.Directive("bind", "textContent", Some(e), _) => Some(IrAttr.BindTextContent(ScalaExpr(e)))

    case Attr.Directive("bind", prop, Some(e), _) if mediaDimensions.contains(prop) =>
      Some(IrAttr.BindMedia(prop, ScalaExpr(e)))

    case Attr.Directive("bind", prop, Some(e), _) if elementDimensions.contains(prop) =>
      Some(IrAttr.BindDimension(prop, ScalaExpr(e)))

    case Attr.Directive("class", name, Some(e), _) => Some(IrAttr.ClassToggle(name, ScalaExpr(e)))
    case Attr.Directive("class", name, None, _)    => Some(IrAttr.ClassToggle(name, ScalaExpr(name)))
    case Attr.Directive("style", prop, Some(e), _) => Some(IrAttr.StyleProp(prop, ScalaExpr(e)))
    case Attr.Directive("use", name, exprOpt, _)   => Some(IrAttr.UseAction(name, exprOpt.map(ScalaExpr(_))))

    case Attr.Directive("transition", name, e, mods) =>
      Some(IrAttr.Transition(TransitionDirection.Both, name, e.map(ScalaExpr(_)), mods.contains("global")))
    case Attr.Directive("in", name, e, mods) =>
      Some(IrAttr.Transition(TransitionDirection.In, name, e.map(ScalaExpr(_)), mods.contains("global")))
    case Attr.Directive("out", name, e, mods) =>
      Some(IrAttr.Transition(TransitionDirection.Out, name, e.map(ScalaExpr(_)), mods.contains("global")))
    case Attr.Directive("animate", name, e, _) =>
      Some(IrAttr.Animate(name, e.map(ScalaExpr(_))))

    case _ => None

  // ── lowerProp ─────────────────────────────────────────────────────────────

  /** Lowers a component attribute into an [[IrProp]], or `None` for attrs that
    * are handled outside the Props constructor (e.g. `bind:this`, `styled`).
    *
    * EventHandler attrs become Props fields with an `on<Event>` name so that
    * components can declare `case class Props(onClick: () => Unit)` etc.
    * `BooleanAttr("styled")` is a CSS-scoping flag consumed by the Emitter
    * directly and must not appear in Props.
    */
  private def lowerProp(attr: Attr): Option[IrProp] = attr match
    case Attr.Static(name, value)       => Some(IrProp(name, IrPropValue.Static(value)))
    case Attr.Dynamic(name, expr)       => Some(IrProp(name, IrPropValue.Dynamic(ScalaExpr(expr))))
    case Attr.Shorthand(varName)        => Some(IrProp(varName, IrPropValue.Shorthand(varName)))
    case Attr.EventHandler(event, expr) =>
      Some(IrProp(s"on${ event.capitalize }", IrPropValue.Dynamic(ScalaExpr(expr))))
    case Attr.BooleanAttr("styled")                 => None // CSS scoping flag, not a Props field
    case Attr.BooleanAttr(name)                     => Some(IrProp(name, IrPropValue.BooleanTrue))
    case Attr.Directive("bind", "this", Some(_), _) =>
      None // bind:this on component handled separately in Emitter
    case _                                          => None

  // ── Window / Body / Document attr lowering ────────────────────────────────

  private val windowBindProps: Set[String] = Set(
    "scrollY",
    "scrollX",
    "innerWidth",
    "innerHeight",
    "outerWidth",
    "outerHeight",
    "devicePixelRatio",
    "online"
  )

  private def lowerWindowAttr(attr: Attr): Option[IrAttr] = attr match
    case Attr.EventHandler(event, expr) => Some(IrAttr.EventHandler(event, ScalaExpr(expr)))
    case Attr.Directive("bind", prop, Some(e), _) if windowBindProps.contains(prop) =>
      Some(IrAttr.BindWindow(prop, ScalaExpr(e)))
    case _ => None

  private def lowerBodyAttr(attr: Attr): Option[IrAttr] = attr match
    case Attr.EventHandler(event, expr)          => Some(IrAttr.EventHandler(event, ScalaExpr(expr)))
    case Attr.Directive("use", name, exprOpt, _) => Some(IrAttr.UseAction(name, exprOpt.map(ScalaExpr(_))))
    case _                                       => None

  private val documentBindProps: Set[String] = Set(
    "visibilityState",
    "fullscreenElement",
    "pointerLockElement",
    "activeElement"
  )

  private def lowerDocumentAttr(attr: Attr): Option[IrAttr] = attr match
    case Attr.EventHandler(event, expr) => Some(IrAttr.EventHandler(event, ScalaExpr(expr)))
    case Attr.Directive("bind", prop, Some(e), _) if documentBindProps.contains(prop) =>
      Some(IrAttr.BindDocument(prop, ScalaExpr(e)))
    case _ => None

  // ── Script body helpers ───────────────────────────────────────────────────

  private def buildPropsType(typeName: String, script: Option[ScriptSection]): IrPropsType =
    val baseName        = extractBaseName(typeName)
    val typeParams      = extractTypeParamStr(typeName)
    val scriptCode      = script.map(_.code).getOrElse("")
    val (typeDecls, _)  = splitTypeDecls(scriptCode)
    val scriptDecl      = typeDecls.mkString("\n\n")
    val allHaveDefaults = allPropsHaveDefaults(scriptDecl)
    IrPropsType(typeName, typeParams, baseName, allHaveDefaults, scriptDecl)

  /** Splits `script` into (typeDecls, restBody).
    * Identical logic to `SpaCodeGen.splitTypeDecls` / `SsrCodeGen.splitTypeDecls`;
    * those duplicates are removed in Phase 2/3.
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

  private def collectBalanced(lines: Vector[String], start: Int): (Int, Vector[String]) =
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
          if seenAnyOpen && depth == 0 then done = true
        case _ => ()
      }
      if !done then i += 1
    (i, buf.toVector)

  private def allPropsHaveDefaults(propsDef: String): Boolean =
    val open  = propsDef.indexOf('(')
    val close = propsDef.lastIndexOf(')')
    if open < 0 || close <= open then true
    else
      val params = propsDef.substring(open + 1, close)
      params.split(",").forall { param =>
        val trimmed = param.trim
        trimmed.isEmpty || trimmed.contains("=")
      }

  private def extractBaseName(typeName: String): String =
    val i = typeName.indexOf('['); if i < 0 then typeName else typeName.substring(0, i)

  private def extractTypeParamStr(typeName: String): String =
    val i = typeName.indexOf('[')
    if i < 0 then ""
    else typeName.substring(i)

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def isStatic(attrs: List[IrAttr], children: List[IrNode]): Boolean =
    attrs.forall(isStaticAttr) && children.forall(isStaticNode)

  private def isStaticAttr(attr: IrAttr): Boolean = attr match
    case IrAttr.StaticAttr(_, _) => true
    case IrAttr.BooleanAttr(_)   => true
    case _                       => false

  private def isStaticNode(node: IrNode): Boolean = node match
    case IrNode.IrStaticText(_)                => true
    case IrNode.IrStaticElement(_, _, a, c, _) => isStatic(a, c)
    case _                                     => false

  private def namespaceFor(tag: String): Option[String] =
    if tag == "svg" then Some("svg")
    else if tag == "math" then Some("math")
    else None

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

  private val mediaDimensions: Set[String] = Set(
    "currentTime",
    "duration",
    "paused",
    "volume",
    "muted",
    "playbackRate",
    "seeking",
    "ended",
    "readyState",
    "videoWidth",
    "videoHeight"
  )

  private val elementDimensions: Set[String] = Set(
    "clientWidth",
    "clientHeight",
    "offsetWidth",
    "offsetHeight"
  )

  // htmlBooleanAttrs is referenced from SpaCodeGen.htmlBooleanAttrs to avoid duplication.
  // In Phase 4 this set will be moved to a shared utility object (e.g. melt.codegen.HtmlBooleanAttrs).
  private val htmlBooleanAttrs: Set[String] = SpaCodeGen.htmlBooleanAttrs
