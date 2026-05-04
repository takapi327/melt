/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.codegen

import meltc.ast.*

/** Generates JVM-targeted HTML string-rendering code from a parsed
  * [[meltc.ast.MeltFile]].
  *
  * Each `.melt` file becomes a Scala `object` with a single
  * `render(props: Props = Props()): RenderResult` method that returns the
  * HTML fragment for the component tree.
  *
  * Selected by `MeltCompiler.compile(...)` when `CompileMode.SSR` is passed.
  *
  * == Phase A scope ==
  *
  * Phase A handles the four core node kinds the design doc lists:
  *
  *   - Element (static + dynamic attributes, void-element detection,
  *     URL attribute validation, CSS scope injection)
  *   - Text
  *   - Expression (`{expr}` — runs through `Escape.html`)
  *   - Component (`<Child />` — merges child `RenderResult` into parent)
  *
  * Event handlers, `bind:`, `use:`, transitions, animations, `melt:window`,
  * and `melt:body` are silently dropped (they have no meaning in SSR).
  * `melt:head` is supported via `ServerRenderer.head.push`. Spread attributes
  * are handled via `ServerRenderer.spreadAttrs`.
  *
  * List rendering (`InlineTemplate`) and conditional blocks are deferred
  * to Phase B.
  *
  * Script sections are emitted verbatim — `Var` / `Signal` on the JVM are
  * no-op wrappers, so reactive code compiles and runs correctly against
  * the initial snapshot.
  */
object SsrCodeGen extends CodeGen:

  def scopeIdFor(objectName: String, filePath: String = ""): String =
    SpaCodeGen.scopeIdFor(objectName, filePath)

  def generate(
    ast:               MeltFile,
    objectName:        String,
    pkg:               String,
    scopeId:           String,
    hydration:         Boolean = false,
    sourcePath:        String  = "",
    scriptBodyLine:    Int     = 1,
    templateStartLine: Int     = 1,
    templateSource:    String  = ""
  ): String =
    val _       = hydration
    val tracker = new LineTracker
    if pkg.nonEmpty then tracker ++= s"package $pkg\n\n"

    tracker ++= "import scala.language.implicitConversions\n"
    tracker ++= "import melt.runtime.*\n"
    if ast.script.flatMap(_.propsType).isDefined then tracker ++= "import melt.runtime.json.PropsCodec\n"
    tracker ++= "import melt.runtime.render.*\n\n"

    tracker ++= s"object $objectName {\n\n"
    tracker ++= s"""  private val _scopeId = "$scopeId"\n\n"""

    val hasCss = ast.style.isDefined
    ast.style.foreach { s =>
      val scoped = CssScoper.scope(s.content, scopeId)
      tracker ++= s"""  private val _css =\n    "${ escapeString(scoped) }"\n\n"""
    }

    val propsType = ast.script.flatMap(_.propsType)

    val scriptBody = ast.script.map(_.code.trim).getOrElse("")

    val (typeDecls, scriptBodyRest) = splitTypeDecls(scriptBody)
    typeDecls.foreach { decl =>
      decl.linesIterator.foreach { line =>
        tracker ++= "  " + line + "\n"
      }
      tracker ++= "\n"
    }

    // When the user chose a type name other than "Props", generate a
    // `val Props = BaseName` value alias so that call sites can always
    // use `ComponentName.Props(...)` regardless of the actual type name.
    propsType.foreach { typeName =>
      val baseName = extractBaseName(typeName)
      if baseName != "Props" then
        val tp = extractTypeParams(typeName)
        tracker ++= s"  val Props = $baseName\n"
        if tp.nonEmpty then tracker ++= s"  type Props$tp = $typeName\n"
        else tracker ++= s"  type Props = $baseName\n"
        tracker += '\n'
    }

    propsType.foreach { tpe =>
      if extractTypeParams(tpe).isEmpty then // skip for generic — PropsCodec.derived doesn't support type params
        tracker ++= s"  private val _propsCodec: PropsCodec[$tpe] = PropsCodec.derived\n\n"
    }

    val _tp         = propsType.map(extractTypeParams).getOrElse("")
    val hasChildren = hasChildrenRef(ast.template)
    tracker ++= s"  def apply$_tp(${ renderParams(propsType, hasChildren) }): RenderResult = {\n"
    tracker ++= "    val renderer = ServerRenderer()\n"
    val moduleId = kebabCase(objectName)
    tracker ++= s"""    renderer.trackComponent("$moduleId")\n"""
    if propsType.exists(extractTypeParams(_).isEmpty) then
      tracker ++= s"""    renderer.trackHydrationProps("$moduleId", _propsCodec.encodeToString(props))\n"""
    if hasCss then tracker ++= "    renderer.css.add(_scopeId, _css)\n"
    tracker ++= "\n"

    // ── Script body section — mark source line for position mapping ─────────
    if scriptBodyRest.nonEmpty then
      tracker ++= "    // ── User script section ──\n"
      tracker.markSourceLine(scriptBodyLine)
      scriptBodyRest.linesIterator.foreach { line =>
        if line.trim.isEmpty then tracker ++= "\n"
        else tracker ++= s"    $line\n"
      }
      tracker ++= "\n"

    tracker ++= s"""    renderer.push(HydrationMarkers.open("$moduleId"))\n"""

    // ── Template section — emit directly into tracker with per-node line marks ─
    // nodeLineOf converts a node's _pos (offset in the template source string) to
    // the corresponding 1-based line number in the original .melt file.
    val nodeLineOf: TemplateNode => Int = node =>
      if node._pos <= 0 || templateSource.isEmpty then templateStartLine
      else templateStartLine + templateSource.take(node._pos).count(_ == '\n')

    tracker ++= "    // ── Template ──\n"
    ast.template.foreach(node => emitNode(node, tracker, indent = 4, scopeId, nodeLineOf = nodeLineOf))

    tracker ++= s"""    renderer.push(HydrationMarkers.close("$moduleId"))\n"""

    tracker ++= "\n    renderer.result()\n"
    tracker ++= "  }\n"
    tracker ++= "}\n"

    // ── Source-map metadata block ─────────────────────────────────────────────
    val linesStr = tracker.linesMetadata()
    val meta =
      if sourcePath.nonEmpty && linesStr.nonEmpty then
        s"\n/*\n    -- MELT GENERATED --\n    SOURCE: $sourcePath\n    LINES: $linesStr\n    -- MELT GENERATED --\n*/\n"
      else ""

    tracker.result() + meta

  private def emitNode(
    node:           TemplateNode,
    buf:            LineTracker,
    indent:         Int,
    scopeId:        String,
    selectBindExpr: Option[String] = None,
    nodeLineOf:     TemplateNode => Int = _ => 1
  ): Unit =
    buf.markSourceLine(nodeLineOf(node))
    val pad = " " * indent
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        tag.toLowerCase match
          case "textarea" if hasBindValue(attrs) =>
            emitTextareaBindValue(attrs, children, buf, indent, scopeId, nodeLineOf)
          case "select" if hasBindValue(attrs) =>
            emitSelectBindValue(tag, attrs, children, buf, indent, scopeId, nodeLineOf)
          case "input" if hasBindGroup(attrs) =>
            emitInputBindGroup(attrs, buf, indent, scopeId)
          case _ if hasBindInnerHtml(attrs) || hasBindTextContent(attrs) =>
            emitElementWithBindContent(tag, attrs, buf, indent, scopeId)
          case _ =>
            emitGenericElement(tag, attrs, children, buf, indent, scopeId, selectBindExpr, nodeLineOf)

      case TemplateNode.Text(content) =>
        val escaped = htmlEscapeLiteral(content)
        buf ++= s"""${ pad }renderer.push("${ escapeString(escaped) }")\n"""

      case TemplateNode.Expression(code) =>
        emitExpression(code, buf, indent, scopeId)

      case TemplateNode.Component(name, attrs, childNodes) =>
        emitComponentCall(name, attrs, buf, indent, childNodes, scopeId, nodeLineOf)

      case TemplateNode.Head(children) =>
        children.foreach(c => emitHeadNode(c, buf, indent, scopeId, nodeLineOf))

      case TemplateNode.Window(_) | TemplateNode.Body(_) | TemplateNode.Document(_) =>
        ()

      case TemplateNode.InlineTemplate(parts) =>
        emitInlineTemplate(parts, buf, indent, scopeId)

      case TemplateNode.DynamicElement(_, _, _) =>
        buf ++= s"${ pad }// TODO(SSR Phase C): DynamicElement\n"

      case TemplateNode.Boundary(_, children, _, _) =>
        children.foreach(c => emitNode(c, buf, indent, scopeId, nodeLineOf = nodeLineOf))

      case TemplateNode.KeyBlock(_, children) =>
        children.foreach(c => emitNode(c, buf, indent, scopeId, nodeLineOf = nodeLineOf))

      case TemplateNode.SnippetDef(_, _, _) => () // SSR: snippets not yet supported

      case TemplateNode.RenderCall(expr) =>
        val pad = " " * indent
        buf ++= s"${ pad }renderer.push($expr)\n"

  /** Generic element emission — the normal open/close path used when no
    * special `bind:` directive is in play. Kept as a separate method so
    * that the special cases above can fall through cleanly.
    */
  private def emitGenericElement(
    tag:            String,
    attrs:          List[Attr],
    children:       List[TemplateNode],
    buf:            LineTracker,
    indent:         Int,
    scopeId:        String,
    selectBindExpr: Option[String],
    nodeLineOf:     TemplateNode => Int = _ => 1
  ): Unit =
    val pad = " " * indent

    emitElementStart(tag, attrs, buf, indent, scopeId)

    if tag.equalsIgnoreCase("option") then
      for
        bindExpr  <- selectBindExpr
        valueExpr <- optionValueExpr(attrs)
      do buf ++= s"""${ pad }if ($bindExpr == $valueExpr) renderer.push(" selected")\n"""

    if HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }renderer.push(">")\n"""
    else
      buf ++= s"""${ pad }renderer.push(">")\n"""
      children.foreach(c => emitNode(c, buf, indent, scopeId, selectBindExpr, nodeLineOf))
      buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

  /** Returns a Scala expression (as a string) for the `value` of an
    * `<option>` element, or `None` if no `value` attribute is present.
    * Static values are quoted literals, dynamic values are the raw user
    * expression.
    */
  private def optionValueExpr(attrs: List[Attr]): Option[String] =
    attrs.collectFirst {
      case Attr.Static("value", v)  => s""""${ escapeString(v) }""""
      case Attr.Dynamic("value", e) => e
    }

  private def hasBindValue(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("bind", "value", Some(_), _) => true
      case _                                           => false
    }

  private def hasBindGroup(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("bind", "group", Some(_), _) => true
      case _                                           => false
    }

  private def hasBindInnerHtml(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("bind", "innerHTML", Some(_), _) => true
      case _                                               => false
    }

  private def hasBindTextContent(attrs: List[Attr]): Boolean =
    attrs.exists {
      case Attr.Directive("bind", "textContent", Some(_), _) => true
      case _                                                 => false
    }

  /** `<textarea bind:value={v}>` — SSR serialisation.
    *
    * Svelte 5 semantics: `value` does NOT appear as an attribute, instead
    * the bound expression becomes the element body. This matches how
    * browsers populate a textarea's displayed text.
    */
  private def emitTextareaBindValue(
    attrs:      List[Attr],
    children:   List[TemplateNode],
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodeLineOf: TemplateNode => Int = _ => 1
  ): Unit =
    val pad      = " " * indent
    val bindExpr = attrs
      .collectFirst {
        case Attr.Directive("bind", "value", Some(e), _) => e
      }
      .getOrElse(sys.error("emitTextareaBindValue called without bind:value — compiler bug"))

    val restAttrs = attrs.filterNot {
      case Attr.Directive("bind", "value", _, _) => true
      case _                                     => false
    }

    emitElementStart("textarea", restAttrs, buf, indent, scopeId)
    buf ++= s"""${ pad }renderer.push(">")\n"""
    children.foreach(c => emitNode(c, buf, indent, scopeId, nodeLineOf = nodeLineOf))
    buf ++= s"""${ pad }renderer.push(Escape.html($bindExpr))\n"""
    buf ++= s"""${ pad }renderer.push("</textarea>")\n"""

  /** `<select bind:value={v}>` — SSR serialisation.
    *
    * Svelte 5 semantics: the bound expression is compared against each
    * `<option>`'s value, and the matching option receives `selected`.
    * We recurse into children via `emitNode(..., selectBindExpr = Some(e))`
    * so that `emitGenericElement` adds the comparison for each option.
    */
  private def emitSelectBindValue(
    tag:        String,
    attrs:      List[Attr],
    children:   List[TemplateNode],
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodeLineOf: TemplateNode => Int = _ => 1
  ): Unit =
    val pad      = " " * indent
    val bindExpr = attrs
      .collectFirst {
        case Attr.Directive("bind", "value", Some(e), _) => e
      }
      .getOrElse(sys.error("emitSelectBindValue called without bind:value — compiler bug"))

    val restAttrs = attrs.filterNot {
      case Attr.Directive("bind", "value", _, _) => true
      case _                                     => false
    }

    emitElementStart(tag, restAttrs, buf, indent, scopeId)
    buf ++= s"""${ pad }renderer.push(">")\n"""
    children.foreach(c => emitNode(c, buf, indent, scopeId, selectBindExpr = Some(bindExpr), nodeLineOf = nodeLineOf))
    buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

  /** `<input type="radio|checkbox" bind:group={arr|single}>` — SSR.
    *
    * For `type="radio"`: emit `checked` iff the bound single value equals
    * the input's `value` attribute.
    * For `type="checkbox"`: emit `checked` iff the bound collection
    * contains the input's `value`.
    *
    * Static `type` attribute is required for Phase B to decide between
    * `==` and `.contains(...)`. Dynamic `type` falls back to radio
    * semantics.
    */
  private def emitInputBindGroup(
    attrs:   List[Attr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad      = " " * indent
    val bindExpr = attrs
      .collectFirst {
        case Attr.Directive("bind", "group", Some(e), _) => e
      }
      .getOrElse(sys.error("emitInputBindGroup called without bind:group — compiler bug"))

    val typeAttr = attrs.collectFirst {
      case Attr.Static("type", t) => t.toLowerCase
    }

    val valueExpr = attrs.collectFirst {
      case Attr.Static("value", v)  => s""""${ escapeString(v) }""""
      case Attr.Dynamic("value", e) => e
    }

    val restAttrs = attrs.filterNot {
      case Attr.Directive("bind", "group", _, _) => true
      case _                                     => false
    }

    emitElementStart("input", restAttrs, buf, indent, scopeId)

    (typeAttr, valueExpr) match
      case (Some("checkbox"), Some(v)) =>
        buf ++= s"""${ pad }if ($bindExpr.contains($v)) renderer.push(" checked")\n"""
      case (_, Some(v)) =>
        buf ++= s"""${ pad }if ($bindExpr == $v) renderer.push(" checked")\n"""
      case _ =>
        ()

    buf ++= s"""${ pad }renderer.push(">")\n"""

  /** Any element with `bind:innerHTML={v}` or `bind:textContent={v}`.
    *
    *   - `bind:innerHTML={v}`: emits `renderer.push(TrustedHtml.value(v))`
    *     — the user is responsible for providing a [[TrustedHtml]].
    *     The Scala type system rejects plain `String` at the call site,
    *     giving compile-time safety.
    *   - `bind:textContent={v}`: emits `renderer.push(Escape.html(v))`
    *     so the value is HTML-escaped.
    *
    * Only one of these directives may be used per element; if both are
    * present, `innerHTML` wins (arbitrarily).
    */
  private def emitElementWithBindContent(
    tag:     String,
    attrs:   List[Attr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent

    val innerHtmlExpr = attrs.collectFirst {
      case Attr.Directive("bind", "innerHTML", Some(e), _) => e
    }
    val textContentExpr =
      if innerHtmlExpr.isDefined then None
      else
        attrs.collectFirst {
          case Attr.Directive("bind", "textContent", Some(e), _) => e
        }

    val restAttrs = attrs.filterNot {
      case Attr.Directive("bind", "innerHTML", _, _)   => true
      case Attr.Directive("bind", "textContent", _, _) => true
      case _                                           => false
    }

    emitElementStart(tag, restAttrs, buf, indent, scopeId)
    if HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }renderer.push(">")\n"""
    else
      buf ++= s"""${ pad }renderer.push(">")\n"""
      innerHtmlExpr match
        case Some(e) =>
          buf ++= s"${ pad }renderer.push($e.value)\n"
        case None =>
          textContentExpr.foreach { e =>
            buf ++= s"${ pad }renderer.push(Escape.html($e))\n"
          }
      buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

  /** Emits the opening portion of an element tag (`<tag class="..."
    * data-foo="..."`), without the closing `>`. The caller closes the tag
    * based on whether it is a void element or has children.
    */
  private def emitElementStart(
    tag:     String,
    attrs:   List[Attr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent
    buf ++= s"""${ pad }renderer.push("<$tag")\n"""
    emitScopedClassAttr(tag, attrs, buf, indent, scopeId)
    attrs.foreach(attr => emitAttr(tag, attr, buf, indent))

  /** Emits the `class="melt-xxxxxx"` (combined with any static `class`
    * attribute and `class:name={expr}` bindings) that scopes the element's CSS.
    * Dynamic class bindings are appended conditionally to the same class
    * attribute so that CSS selectors like `.foo.active` work correctly.
    */
  private def emitScopedClassAttr(
    tag:     String,
    attrs:   List[Attr],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad           = " " * indent
    val staticClass   = attrs.collectFirst { case Attr.Static("class", v) => v }
    val classBindings = attrs.collect {
      case Attr.Directive("class", name, Some(expr), _) => (name, expr)
    }
    val baseClass = staticClass.map(cls => s"$cls $scopeId").getOrElse(scopeId)

    if classBindings.isEmpty then
      val combined = escapeString(s""" class=\"$baseClass\"""")
      buf ++= s"""${ pad }renderer.push("$combined")\n"""
    else
      val condParts = classBindings
        .map {
          case (name, expr) =>
            s"""(if ($expr) " $name" else "")"""
        }
        .mkString(" + ")
      buf ++= s"""${ pad }renderer.push(" class=\\"${ escapeString(baseClass) }" + $condParts + "\\"")\n"""

  /** Emits one non-`class`-static attribute. */
  private def emitAttr(
    tag:    String,
    attr:   Attr,
    buf:    LineTracker,
    indent: Int
  ): Unit =
    val pad = " " * indent
    attr match
      case Attr.Static("class", _) => ()

      case Attr.Static(name, value) =>
        val combined = escapeString(s""" $name=\"${ htmlAttrEscapeLiteral(value) }\"""")
        buf ++= s"""${ pad }renderer.push("$combined")\n"""

      case Attr.BooleanAttr(name) =>
        val lit = escapeString(s" $name")
        buf ++= s"""${ pad }renderer.push("$lit")\n"""

      case Attr.Dynamic(name, expr) =>
        if UrlAttributesForCodegen.isUrlAttribute(tag, name) then
          buf ++= s"""${ pad }renderer.push(s\" $name=\\\"\" + Escape.url($expr) + \"\\\"\")\n"""
        else buf ++= s"""${ pad }renderer.push(s\" $name=\\\"\" + Escape.attr($expr) + \"\\\"\")\n"""

      case Attr.Shorthand(varName) =>
        if UrlAttributesForCodegen.isUrlAttribute(tag, varName) then
          buf ++= s"""${ pad }renderer.push(s\" $varName=\\\"\" + Escape.url($varName) + \"\\\"\")\n"""
        else buf ++= s"""${ pad }renderer.push(s\" $varName=\\\"\" + Escape.attr($varName) + \"\\\"\")\n"""

      case Attr.Spread(expr) =>
        buf ++= s"""${ pad }renderer.spreadAttrs("$tag", $expr)\n"""

      case Attr.EventHandler(_, _) =>
        ()

      case Attr.Directive(kind, name, expr, _) =>
        kind match
          case "bind" =>
            expr.foreach { e =>
              buf ++= s"""${ pad }renderer.push(s\" $name=\\\"\" + Escape.attr($e) + \"\\\"\")\n"""
            }
          case "class" =>
            () // handled in emitScopedClassAttr — merged into the class attribute
          case "style" =>
            expr.foreach { e =>
              buf ++= s"""${ pad }renderer.push(s\" style=\\\"$name:\" + Escape.cssValue($e) + \";\\\"\")\n"""
            }
          case _ =>
            ()

  /** Emits a `<Child ... />` component invocation.
    *
    * When `childNodes` is non-empty the children lambda is emitted as
    * multi-line code so that [[LineTracker.markSourceLine]] calls inside
    * [[emitNodeToSb]] can produce one LINES entry per node.
    */
  private def emitComponentCall(
    name:       String,
    attrs:      List[Attr],
    buf:        LineTracker,
    indent:     Int,
    childNodes: List[TemplateNode] = Nil,
    scopeId:    String = "",
    nodeLineOf: TemplateNode => Int = _ => 1
  ): Unit =
    val pad  = " " * indent
    val args = attrs.flatMap {
      case Attr.Shorthand(n)  => Some(s"$n = $n")
      case Attr.Static(n, v)  => Some(s"""$n = \"${ escapeString(v) }\"""")
      case Attr.Dynamic(n, e) => Some(s"$n = $e")
      case Attr.Spread(_)     => None
      case _                  => None
    }

    if childNodes.isEmpty then
      if args.isEmpty then buf ++= s"${ pad }renderer.merge($name())\n"
      else buf ++= s"${ pad }renderer.merge($name($name.Props(${ args.mkString(", ") })))\n"
    else
      // Emit children as multi-line lambda so each node gets its own generated
      // line and markSourceLine can map it back to the original .melt source.
      val propsStr = if args.nonEmpty then s"$name.Props(${ args.mkString(", ") }), " else ""
      buf ++= s"${ pad }renderer.merge($name(${propsStr}children = () => {\n"
      buf ++= s"${ pad }  val _sb = new StringBuilder\n"
      childNodes.foreach(c => emitNodeToSb(c, buf, s"$pad  ", scopeId, nodeLineOf))
      buf ++= s"${ pad }  RenderResult(_sb.toString, \"\")\n"
      buf ++= s"${ pad }}))\n"

  /** Emits a `TemplateNode.Expression`.
    *
    * We dispatch on the shape of the user expression:
    *
    *   - `source.map(domBody)` / `source.keyed(k).map(domBody)` (Phase A
    *     produced [[InlineTemplate]] before reaching this branch, so any
    *     `.map(` that survives here produced plain values and is treated
    *     as a normal expression)
    *   - `if cond then <dom> else <dom>` / `match ... => <dom>` — arrive
    *     via [[InlineTemplate]] after expansion; straight [[Expression]]
    *     nodes here contain plain Scala and are pushed through
    *     `Escape.html`
    *   - anything else is treated as plain text
    */
  private def emitExpression(
    code:    String,
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent
    if code.trim == "children" then buf ++= s"${ pad }renderer.merge(children())\n"
    else if code.trim.contains("TrustedHtml") then buf ++= s"""${ pad }renderer.push(($code).value)\n"""
    else buf ++= s"""${ pad }renderer.push(Escape.html($code))\n"""

  /** Emits a `TemplateNode.InlineTemplate`.
    *
    * InlineTemplate is the AST representation produced by the parser when
    * an expression contains inline HTML fragments — typically list
    * rendering (`{items.map(item => <li>{item}</li>)}`) or conditional
    * rendering (`{if cond then <p>yes</p> else <p>no</p>}`).
    *
    * We expand each `Html` part into a Scala block that returns a
    * `String` built via a local [[StringBuilder]], producing a single
    * Scala expression that evaluates to either a `String` (conditional)
    * or a `Seq[String]` / `IterableOnce[String]` (list rendering).
    *
    * The expression is then wrapped in `renderer.push(expression)` /
    * `expression.foreach(renderer.push)` depending on its shape:
    *
    *   - If the expression produces an iterable (detected heuristically
    *     by the presence of an unterminated `.map(` in the `Code` parts
    *     before the `Html` fragment), wrap in `.foreach`
    *   - Otherwise push the expression directly as a single `String`
    */
  private def emitInlineTemplate(
    parts:   List[InlineTemplatePart],
    buf:     LineTracker,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent

    val exprBuf = new StringBuilder
    parts.foreach {
      case InlineTemplatePart.Code(code) =>
        exprBuf ++= code
      case InlineTemplatePart.Html(nodes) =>
        exprBuf ++= htmlNodesToStringExpr(nodes, scopeId)
    }

    val expr = exprBuf.toString
    val kind = classifyInlineExpr(expr, parts)

    kind match
      case InlineKind.Iterable =>
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }$expr.foreach(renderer.push)\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""

      case InlineKind.SingleString =>
        buf ++= s"""${ pad }renderer.push("<!--[melt:dyn-->")\n"""
        buf ++= s"${ pad }renderer.push($expr)\n"
        buf ++= s"""${ pad }renderer.push("<!--]melt:dyn-->")\n"""

  /** Converts a list of `TemplateNode`s from an `InlineTemplatePart.Html`
    * into a Scala expression that returns the corresponding HTML string.
    *
    * This is the SSR counterpart of `SpaCodeGen`'s DOM-construction
    * expansion: rather than `dom.document.createElement(...)` we emit
    * `StringBuilder` blocks that concatenate the HTML serialisation.
    */
  private def htmlNodesToStringExpr(
    nodes:   List[TemplateNode],
    scopeId: String
  ): String =
    val sb = new StringBuilder
    sb ++= "{ val _sb = new StringBuilder; "
    nodes.foreach(n => appendNodeToSb(n, sb, scopeId))
    sb ++= "_sb.toString }"
    sb.toString

  /** Appends code that writes a single template node into the local
    * StringBuilder `_sb`. Mirrors `emitNode` for the body stream but
    * targets a `_sb ++=` write pattern instead of `renderer.push`.
    */
  private def appendNodeToSb(
    node:    TemplateNode,
    sb:      StringBuilder,
    scopeId: String
  ): Unit = node match
    case TemplateNode.Element(tag, attrs, children) =>
      sb ++= s"""_sb ++= "<$tag"; """
      val staticClass   = attrs.collectFirst { case Attr.Static("class", v) => v }
      val classBindings = attrs.collect {
        case Attr.Directive("class", name, Some(expr), _) => (name, expr)
      }
      val baseClass = staticClass.map(cls => s"$cls $scopeId").getOrElse(scopeId)

      if classBindings.isEmpty then sb ++= s"""_sb ++= " class=\\"${ escapeString(baseClass) }\\""; """
      else
        val condParts = classBindings
          .map {
            case (name, expr) =>
              s"""(if ($expr) " $name" else "")"""
          }
          .mkString(" + ")
        sb ++= s"""_sb ++= " class=\\"${ escapeString(baseClass) }" + $condParts + "\\""; """

      attrs.foreach {
        case Attr.Static("class", _)  => ()
        case Attr.Static(name, value) =>
          val lit = escapeString(s""" $name=\"${ htmlAttrEscapeLiteral(value) }\"""")
          sb ++= s"""_sb ++= "$lit"; """
        case Attr.BooleanAttr(name) =>
          val lit = escapeString(s" $name")
          sb ++= s"""_sb ++= "$lit"; """
        case Attr.Dynamic(name, expr) =>
          if UrlAttributesForCodegen.isUrlAttribute(tag, name) then
            sb ++= s"""_sb ++= s\" $name=\\\"\" + Escape.url($expr) + \"\\\"\"; """
          else sb ++= s"""_sb ++= s\" $name=\\\"\" + Escape.attr($expr) + \"\\\"\"; """
        case Attr.Shorthand(varName) =>
          if UrlAttributesForCodegen.isUrlAttribute(tag, varName) then
            sb ++= s"""_sb ++= s\" $varName=\\\"\" + Escape.url($varName) + \"\\\"\"; """
          else sb ++= s"""_sb ++= s\" $varName=\\\"\" + Escape.attr($varName) + \"\\\"\"; """
        case Attr.EventHandler(_, _)    => ()
        case Attr.Directive(_, _, _, _) => ()
        case Attr.Spread(_)             => ()
      }

      sb ++= s"""_sb ++= ">"; """
      if !HtmlVoidElements.isVoid(tag) then
        children.foreach(c => appendNodeToSb(c, sb, scopeId))
        sb ++= s"""_sb ++= "</$tag>"; """

    case TemplateNode.Text(content) =>
      val escaped = escapeString(htmlEscapeLiteral(content))
      sb ++= s"""_sb ++= "$escaped"; """

    case TemplateNode.Expression(code) =>
      if code.trim == "children" then sb ++= """{ val _r = children(); renderer.mergeMeta(_r); _sb ++= _r.body }; """
      else if code.trim.contains("TrustedHtml") then sb ++= s"""_sb ++= ($code).value; """
      else sb ++= s"""_sb ++= Escape.html($code); """

    case TemplateNode.Component(name, attrs, childNodes) =>
      val args = attrs.flatMap {
        case Attr.Shorthand(n)  => Some(s"$n = $n")
        case Attr.Static(n, v)  => Some(s"""$n = \"${ escapeString(v) }\"""")
        case Attr.Dynamic(n, e) => Some(s"$n = $e")
        case _                  => None
      }
      val childrenArg: Option[String] =
        if childNodes.nonEmpty then Some(s"children = ${ buildSsrChildrenLambdaExpr(childNodes, scopeId) }")
        else None
      val call =
        if args.isEmpty && childrenArg.isEmpty then s"$name()"
        else if args.isEmpty then s"$name(${ childrenArg.get })"
        else if childrenArg.isEmpty then s"$name($name.Props(${ args.mkString(", ") }))"
        else s"$name($name.Props(${ args.mkString(", ") }), ${ childrenArg.get })"
      // Use mergeMeta so CSS, head content, and hydration-component tracking
      // from the child are propagated to the enclosing renderer while the
      // body is appended to the local StringBuilder (avoiding a double-push).
      sb ++= s"""{ val _r = $call; renderer.mergeMeta(_r); _sb ++= _r.body }; """

    case TemplateNode.InlineTemplate(nested) =>
      val exprBuf = new StringBuilder
      nested.foreach {
        case InlineTemplatePart.Code(code)  => exprBuf ++= code
        case InlineTemplatePart.Html(nodes) => exprBuf ++= htmlNodesToStringExpr(nodes, scopeId)
      }
      val expr = exprBuf.toString
      val kind = classifyInlineExpr(expr, nested)
      sb ++= """_sb ++= "<!--[melt:dyn-->"; """
      kind match
        case InlineKind.Iterable =>
          sb ++= s"$expr.foreach(s => _sb ++= s); "
        case InlineKind.SingleString =>
          sb ++= s"_sb ++= ($expr); "
      sb ++= """_sb ++= "<!--]melt:dyn-->"; """

    case _ =>
      ()

  private enum InlineKind:
    case Iterable
    case SingleString

  /** Very small heuristic to detect whether an inline expression is
    * producing an `Iterable[String]` (list rendering) or a single
    * `String` (conditional / wrap).
    *
    * We look at the concatenated `Code` parts for an unterminated
    * `.map(` or `.keyed(...).map(` at the outermost level. This is the
    * same detection used by the existing SPA `classifyExpr`.
    */
  private def classifyInlineExpr(expr: String, parts: List[InlineTemplatePart]): InlineKind =
    val codeOnly = parts.collect { case InlineTemplatePart.Code(c) => c }.mkString
    val trimmed  = codeOnly.trim
    if trimmed.contains(".map(") || trimmed.contains(".keyed(") then InlineKind.Iterable
    else InlineKind.SingleString

  /** Recursively emits a child of `<melt:head>` so that the HTML ends up
    * in `renderer.head` rather than the body buffer.
    */
  private def emitHeadNode(
    node:       TemplateNode,
    buf:        LineTracker,
    indent:     Int,
    scopeId:    String,
    nodeLineOf: TemplateNode => Int = _ => 1
  ): Unit =
    val pad = " " * indent
    node match
      case TemplateNode.Element("title", _, titleChildren) =>
        titleChildren match
          case TemplateNode.Expression(code) :: Nil =>
            buf ++= s"${ pad }renderer.head.title($code)\n"
          case _ =>
            val staticText = titleChildren.collect {
              case TemplateNode.Text(t) => t
            }.mkString
            buf ++= s"""${ pad }renderer.head.push("<title>${ escapeString(
                htmlEscapeLiteral(staticText)
              ) }</title>")\n"""

      case TemplateNode.Element(tag, attrs, children) =>
        buf ++= s"""${ pad }renderer.head.push("<$tag")\n"""
        attrs.foreach {
          case Attr.Static(n, v) =>
            val lit = escapeString(s""" $n=\"${ htmlAttrEscapeLiteral(v) }\"""")
            buf ++= s"""${ pad }renderer.head.push("$lit")\n"""
          case Attr.Dynamic(n, e) =>
            buf ++= s"""${ pad }renderer.head.push(s\" $n=\\\"\" + Escape.attr($e) + \"\\\"\")\n"""
          case _ => ()
        }
        if HtmlVoidElements.isVoid(tag) then buf ++= s"""${ pad }renderer.head.push(">")\n"""
        else
          buf ++= s"""${ pad }renderer.head.push(">")\n"""
          children.foreach(c => emitHeadNode(c, buf, indent, scopeId, nodeLineOf))
          buf ++= s"""${ pad }renderer.head.push("</$tag>")\n"""

      case TemplateNode.Text(content) =>
        buf ++= s"""${ pad }renderer.head.push("${ escapeString(htmlEscapeLiteral(content)) }")\n"""

      case TemplateNode.Expression(code) =>
        buf ++= s"${ pad }renderer.head.push(Escape.html($code))\n"

      case _ =>
        ()

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

  private def renderParams(propsType: Option[String], hasChildren: Boolean = false): String =
    val propsPart = propsType match
      case Some(tpe) if extractTypeParams(tpe).nonEmpty => s"props: $tpe"
      case Some(tpe)                                    => s"props: $tpe = $tpe()"
      case None                                         => ""
    val childrenPart =
      if hasChildren then "children: () => RenderResult = () => RenderResult.empty"
      else ""
    List(propsPart, childrenPart).filter(_.nonEmpty).mkString(", ")

  /** Builds a `() => RenderResult` lambda expression (as a code string) that
    * renders `childNodes` using [[appendNodeToSb]] so they can be passed as
    * the `children` parameter to a component call.
    *
    * The generated lambda captures `renderer` and `_scopeId` from the enclosing
    * `apply()` body — sub-component metadata (CSS, head) is merged into the outer
    * renderer via `renderer.mergeMeta(...)`.
    */
  private def buildSsrChildrenLambdaExpr(
    childNodes: List[TemplateNode],
    scopeId:    String
  ): String =
    val sb = new StringBuilder
    sb ++= "() => { val _sb = new StringBuilder; "
    childNodes.foreach(n => appendNodeToSb(n, sb, scopeId))
    sb ++= "RenderResult(_sb.toString, \"\") }"
    sb.toString

  /** Emits a single template node into `buf` as `_sb ++=` statements, one per
    * generated line, calling [[LineTracker.markSourceLine]] before each node so
    * that the source-map `LINES:` field contains per-node entries.
    *
    * Used inside component children lambdas emitted by [[emitComponentCall]].
    * For node types that are complex to expand (Component, InlineTemplate, …),
    * the method falls back to the [[appendNodeToSb]] inline style so that
    * correctness is preserved even if per-node granularity is lost.
    */
  private def emitNodeToSb(
    node:       TemplateNode,
    buf:        LineTracker,
    pad:        String,
    scopeId:    String,
    nodeLineOf: TemplateNode => Int
  ): Unit =
    buf.markSourceLine(nodeLineOf(node))
    node match
      case TemplateNode.Text(content) =>
        val escaped = escapeString(htmlEscapeLiteral(content))
        if escaped.nonEmpty then buf ++= s"""${pad}_sb ++= "$escaped"\n"""

      case TemplateNode.Expression(code) =>
        if code.trim == "children" then
          buf ++= s"""${pad}{ val _r = children(); renderer.mergeMeta(_r); _sb ++= _r.body }\n"""
        else if code.trim.contains("TrustedHtml") then
          buf ++= s"""${pad}_sb ++= ($code).value\n"""
        else
          buf ++= s"""${pad}_sb ++= Escape.html($code)\n"""

      case TemplateNode.Element(tag, attrs, children) =>
        // Build opening tag (with all attributes) as a single line, then recurse
        // into children so each gets its own line and source-map entry.
        val open          = new StringBuilder
        val staticClass   = attrs.collectFirst { case Attr.Static("class", v) => v }
        val classBindings = attrs.collect { case Attr.Directive("class", name, Some(expr), _) => (name, expr) }
        val baseClass     = staticClass.map(cls => s"$cls $scopeId").getOrElse(scopeId)
        open ++= s"""_sb ++= "<$tag""""
        if classBindings.isEmpty then open ++= s"""; _sb ++= " class=\\"${ escapeString(baseClass) }\\""; """
        else
          val condParts = classBindings.map { case (n, e) => s"""(if ($e) " $n" else "")""" }.mkString(" + ")
          open ++= s"""; _sb ++= " class=\\"${ escapeString(baseClass) }" + $condParts + "\\""; """
        attrs.foreach {
          case Attr.Static("class", _) => ()
          case Attr.Static(name, value) =>
            val lit = escapeString(s""" $name=\"${ htmlAttrEscapeLiteral(value) }\"""")
            open ++= s"""; _sb ++= "$lit""""
          case Attr.BooleanAttr(name) =>
            val lit = escapeString(s" $name")
            open ++= s"""; _sb ++= "$lit""""
          case Attr.Dynamic(name, expr) =>
            if UrlAttributesForCodegen.isUrlAttribute(tag, name) then
              open ++= s"""; _sb ++= s\" $name=\\\"\" + Escape.url($expr) + \"\\\"\""""
            else open ++= s"""; _sb ++= s\" $name=\\\"\" + Escape.attr($expr) + \"\\\"\""""
          case Attr.Shorthand(varName) =>
            if UrlAttributesForCodegen.isUrlAttribute(tag, varName) then
              open ++= s"""; _sb ++= s\" $varName=\\\"\" + Escape.url($varName) + \"\\\"\""""
            else open ++= s"""; _sb ++= s\" $varName=\\\"\" + Escape.attr($varName) + \"\\\"\""""
          case _ => ()
        }
        open ++= s"""; _sb ++= ">""""
        buf ++= s"$pad${open.toString}\n"
        children.foreach(c => emitNodeToSb(c, buf, pad, scopeId, nodeLineOf))
        if !HtmlVoidElements.isVoid(tag) then buf ++= s"""${pad}_sb ++= "</$tag>"\n"""

      case _ =>
        // Fall back to inline style for Component, InlineTemplate, etc.
        val nodeSb = new StringBuilder
        appendNodeToSb(node, nodeSb, scopeId)
        val inline = nodeSb.toString
        if inline.nonEmpty then buf ++= s"$pad${inline.stripSuffix("; ")}\n"

  /** Returns `true` if `nodes` (or any of their descendants) contain
    * `{children}` — i.e. a [[TemplateNode.Expression]] whose code is
    * exactly `"children"`.  Used by [[generate]] to decide whether the
    * generated `apply()` should accept a `children` parameter.
    */
  private def hasChildrenRef(nodes: List[TemplateNode]): Boolean =
    nodes.exists {
      case TemplateNode.Expression(code)      => code.trim == "children"
      case TemplateNode.Element(_, _, ch)     => hasChildrenRef(ch)
      case TemplateNode.Component(_, _, ch)   => hasChildrenRef(ch)
      case TemplateNode.InlineTemplate(parts) =>
        parts.exists {
          case InlineTemplatePart.Html(ns) => hasChildrenRef(ns)
          case _                           => false
        }
      case TemplateNode.Head(ch)                      => hasChildrenRef(ch)
      case TemplateNode.Boundary(_, ch, pend, failed) =>
        hasChildrenRef(ch) ||
        pend.exists(p => hasChildrenRef(p.children)) ||
        failed.exists(f => hasChildrenRef(f.children))
      case TemplateNode.KeyBlock(_, ch)      => hasChildrenRef(ch)
      case TemplateNode.SnippetDef(_, _, ch) => hasChildrenRef(ch)
      case _                                 => false
    }

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

  /** Escapes a Scala string literal (backslash, quotes, newlines). */
  private def escapeString(s: String): String =
    val buf = new StringBuilder(s.length)
    s.foreach {
      case '\\' => buf ++= "\\\\"
      case '"'  => buf ++= "\\\""
      case '\n' => buf ++= "\\n"
      case '\r' => buf ++= "\\r"
      case '\t' => buf ++= "\\t"
      case c    => buf += c
    }
    buf.toString

  /** HTML-escapes a compile-time-known literal (for static text nodes and
    * static attribute values). Runtime-escape still happens via
    * `Escape.html` / `Escape.attr` for dynamic content.
    */
  private def htmlEscapeLiteral(s: String): String =
    val buf = new StringBuilder(s.length)
    s.foreach {
      case '&' => buf ++= "&amp;"
      case '<' => buf ++= "&lt;"
      case '>' => buf ++= "&gt;"
      case c   => buf += c
    }
    buf.toString

  /** Attribute-value variant — also escapes `"`. */
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

  /** Splits a user's script body into:
    *
    *   - `typeDecls` — each element is the full text of one top-level
    *     type definition (`case class`, `type`, `sealed trait`, `enum`),
    *     in source order
    *   - `rest` — everything else in its original order, with the
    *     extracted type declarations removed
    *
    * Type declarations are hoisted to the object level so that Props
    * (and any types referenced by Props) are visible to external
    * callers via `ComponentName.Props(...)` / `ComponentName.Todo(...)`.
    * Value expressions (vals, side-effects, var declarations) stay
    * inside the per-render body.
    *
    * The extractor is paren-balanced: a `case class Foo(` on one line
    * with closing `)` on a later line is captured as a single decl.
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

  /** Returns `true` if the trimmed line starts a top-level type
    * declaration that should be hoisted to the object level.
    */
  private def isTypeDeclStart(trimmed: String): Boolean =
    trimmed.startsWith("case class ") ||
      trimmed.startsWith("type ") ||
      trimmed.startsWith("sealed trait ") ||
      trimmed.startsWith("sealed abstract class ") ||
      trimmed.startsWith("enum ")

  /** Greedy extraction of a balanced declaration starting at `start`.
    * Walks forward until the `(` / `{` / `[` counters return to zero,
    * handling multi-line case-class definitions.
    *
    * @return `(lastLineIdx, linesIncluded)` — the last line index
    *         consumed (inclusive) and the lines that form the full
    *         declaration.
    */
  private def collectBalanced(
    lines: Vector[String],
    start: Int
  ): (Int, Vector[String]) =
    var depth:       Int     = 0
    var seenAnyOpen: Boolean = false
    val buf  = scala.collection.mutable.ListBuffer.empty[String]
    var i    = start
    var done = false
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

/** Local copy of the runtime's URL attribute list, used by `SsrCodeGen` at
  * compile time. Kept in sync with `melt.runtime.UrlAttributes`.
  *
  * Duplication rationale is the same as `HtmlVoidElements`: meltc must not
  * depend on the runtime library.
  */
private object UrlAttributesForCodegen:

  private val specific: Set[(String, String)] = Set(
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

  private val global: Set[String] = Set("xlink:href")

  def isUrlAttribute(tag: String, attrName: String): Boolean =
    val t = tag.toLowerCase
    val a = attrName.toLowerCase
    global.contains(a) || specific.contains((t, a))
