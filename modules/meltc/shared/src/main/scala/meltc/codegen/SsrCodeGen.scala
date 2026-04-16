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
  * `melt:head` is supported via `SsrRenderer.head.push`. Spread attributes
  * are handled via `SsrRenderer.spreadAttrs`.
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
    ast:        MeltFile,
    objectName: String,
    pkg:        String,
    scopeId:    String,
    hydration:  Boolean = false
  ): String =
    val _   = hydration
    val buf = new StringBuilder
    if pkg.nonEmpty then buf ++= s"package $pkg\n\n"

    buf ++= "import scala.language.implicitConversions\n"
    buf ++= "import melt.runtime.*\n"
    if ast.script.flatMap(_.propsType).isDefined then buf ++= "import melt.runtime.json.PropsCodec\n"
    buf ++= "import melt.runtime.ssr.*\n\n"

    buf ++= s"object $objectName {\n\n"
    buf ++= s"""  private val _scopeId = "$scopeId"\n\n"""

    val hasCss = ast.style.isDefined
    ast.style.foreach { s =>
      val scoped = CssScoper.scope(s.css, scopeId)
      buf ++= s"""  private val _css =\n    "${ escapeString(scoped) }"\n\n"""
    }

    val propsType = ast.script.flatMap(_.propsType)

    val scriptBody = ast.script.map(_.code.trim).getOrElse("")

    val (typeDecls, scriptBodyRest) = splitTypeDecls(scriptBody)
    typeDecls.foreach { decl =>
      decl.linesIterator.foreach { line =>
        buf ++= "  " + line + "\n"
      }
      buf ++= "\n"
    }

    propsType.foreach { tpe =>
      buf ++= s"  private val _propsCodec: PropsCodec[$tpe] = PropsCodec.derived\n\n"
    }

    buf ++= s"  def apply(${ renderParams(propsType) }): RenderResult = {\n"
    buf ++= "    val renderer = SsrRenderer()\n"
    val moduleId = kebabCase(objectName)
    buf ++= s"""    renderer.trackComponent("$moduleId")\n"""
    if propsType.isDefined then
      buf ++= s"""    renderer.trackHydrationProps("$moduleId", _propsCodec.encodeToString(props))\n"""
    if hasCss then buf ++= "    renderer.css.add(_scopeId, _css)\n"
    buf ++= "\n"

    if scriptBodyRest.nonEmpty then
      buf ++= "    // ── User script section ──\n"
      scriptBodyRest.linesIterator.foreach { line =>
        if line.trim.isEmpty then buf ++= "\n"
        else buf ++= s"    $line\n"
      }
      buf ++= "\n"

    buf ++= s"""    renderer.push(HydrationMarkers.open("$moduleId"))\n"""

    buf ++= "    // ── Template ──\n"
    ast.template.foreach(node => emitNode(node, buf, indent = 4, scopeId))

    buf ++= s"""    renderer.push(HydrationMarkers.close("$moduleId"))\n"""

    buf ++= "\n    renderer.result()\n"
    buf ++= "  }\n"
    buf ++= "}\n"

    buf.toString

  private def emitNode(
    node:           TemplateNode,
    buf:            StringBuilder,
    indent:         Int,
    scopeId:        String,
    selectBindExpr: Option[String] = None
  ): Unit =
    val pad = " " * indent
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        tag.toLowerCase match
          case "textarea" if hasBindValue(attrs) =>
            emitTextareaBindValue(attrs, children, buf, indent, scopeId)
          case "select" if hasBindValue(attrs) =>
            emitSelectBindValue(tag, attrs, children, buf, indent, scopeId)
          case "input" if hasBindGroup(attrs) =>
            emitInputBindGroup(attrs, buf, indent, scopeId)
          case _ if hasBindInnerHtml(attrs) || hasBindTextContent(attrs) =>
            emitElementWithBindContent(tag, attrs, buf, indent, scopeId)
          case _ =>
            emitGenericElement(tag, attrs, children, buf, indent, scopeId, selectBindExpr)

      case TemplateNode.Text(content) =>
        val escaped = htmlEscapeLiteral(content)
        buf ++= s"""${ pad }renderer.push("${ escapeString(escaped) }")\n"""

      case TemplateNode.Expression(code) =>
        emitExpression(code, buf, indent, scopeId)

      case TemplateNode.Component(name, attrs, _) =>
        emitComponentCall(name, attrs, buf, indent)

      case TemplateNode.Head(children) =>
        children.foreach(c => emitHeadNode(c, buf, indent, scopeId))

      case TemplateNode.Window(_) | TemplateNode.Body(_) | TemplateNode.Document(_) =>
        ()

      case TemplateNode.InlineTemplate(parts) =>
        emitInlineTemplate(parts, buf, indent, scopeId)

      case TemplateNode.DynamicElement(_, _, _) =>
        buf ++= s"${ pad }// TODO(SSR Phase C): DynamicElement\n"

      case TemplateNode.Boundary(_, children, _, _) =>
        children.foreach(c => emitNode(c, buf, indent, scopeId))

  /** Generic element emission — the normal open/close path used when no
    * special `bind:` directive is in play. Kept as a separate method so
    * that the special cases above can fall through cleanly.
    */
  private def emitGenericElement(
    tag:            String,
    attrs:          List[Attr],
    children:       List[TemplateNode],
    buf:            StringBuilder,
    indent:         Int,
    scopeId:        String,
    selectBindExpr: Option[String]
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
      children.foreach(c => emitNode(c, buf, indent, scopeId, selectBindExpr))
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
    attrs:    List[Attr],
    children: List[TemplateNode],
    buf:      StringBuilder,
    indent:   Int,
    scopeId:  String
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
    children.foreach(c => emitNode(c, buf, indent, scopeId))
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
    tag:      String,
    attrs:    List[Attr],
    children: List[TemplateNode],
    buf:      StringBuilder,
    indent:   Int,
    scopeId:  String
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
    children.foreach(c => emitNode(c, buf, indent, scopeId, selectBindExpr = Some(bindExpr)))
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
    buf:     StringBuilder,
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
    buf:     StringBuilder,
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
    buf:     StringBuilder,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent
    buf ++= s"""${ pad }renderer.push("<$tag")\n"""
    emitScopedClassAttr(tag, attrs, buf, indent, scopeId)
    attrs.foreach(attr => emitAttr(tag, attr, buf, indent))

  /** Emits the `class="melt-xxxxxx"` (combined with any static `class`
    * attribute) that scopes the element's CSS. If there is an additional
    * dynamic `class=`, it is not handled in Phase A — the caller keeps
    * its own `class` attribute as-is.
    */
  private def emitScopedClassAttr(
    tag:     String,
    attrs:   List[Attr],
    buf:     StringBuilder,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad         = " " * indent
    val staticClass = attrs.collectFirst {
      case Attr.Static("class", v) => v
    }
    staticClass match
      case Some(cls) =>
        val combined = escapeString(s""" class=\"$cls $scopeId\"""")
        buf ++= s"""${ pad }renderer.push("$combined")\n"""
      case None =>
        val combined = escapeString(s""" class=\"$scopeId\"""")
        buf ++= s"""${ pad }renderer.push("$combined")\n"""

  /** Emits one non-`class`-static attribute. */
  private def emitAttr(
    tag:    String,
    attr:   Attr,
    buf:    StringBuilder,
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
            expr.foreach { e =>
              buf ++= s"""${ pad }if ($e) then renderer.push(" $name")\n"""
            }
          case "style" =>
            expr.foreach { e =>
              buf ++= s"""${ pad }renderer.push(s\" style=\\\"$name:\" + Escape.cssValue($e) + \";\\\"\")\n"""
            }
          case _ =>
            ()

  /** Emits a `<Child ... />` component invocation. */
  private def emitComponentCall(
    name:   String,
    attrs:  List[Attr],
    buf:    StringBuilder,
    indent: Int
  ): Unit =
    val pad  = " " * indent
    val args = attrs.flatMap {
      case Attr.Shorthand(n)  => Some(s"$n = $n")
      case Attr.Static(n, v)  => Some(s"""$n = \"${ escapeString(v) }\"""")
      case Attr.Dynamic(n, e) => Some(s"$n = $e")
      case Attr.Spread(_)     => None
      case _                  => None
    }

    if args.isEmpty then buf ++= s"${ pad }renderer.merge($name())\n"
    else buf ++= s"${ pad }renderer.merge($name($name.Props(${ args.mkString(", ") })))\n"

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
    buf:     StringBuilder,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent
    buf ++= s"""${ pad }renderer.push(Escape.html($code))\n"""

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
    buf:     StringBuilder,
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
        buf ++= s"${ pad }$expr.foreach(renderer.push)\n"

      case InlineKind.SingleString =>
        buf ++= s"${ pad }renderer.push($expr)\n"

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
      val staticClass = attrs.collectFirst {
        case Attr.Static("class", v) => v
      }
      staticClass match
        case Some(cls) =>
          sb ++= s"""_sb ++= " class=\\"${ escapeString(cls) } $scopeId\\""; """
        case None =>
          sb ++= s"""_sb ++= " class=\\"$scopeId\\""; """

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
      sb ++= s"""_sb ++= Escape.html($code); """

    case TemplateNode.Component(name, attrs, _) =>
      val args = attrs.flatMap {
        case Attr.Shorthand(n)  => Some(s"$n = $n")
        case Attr.Static(n, v)  => Some(s"""$n = \"${ escapeString(v) }\"""")
        case Attr.Dynamic(n, e) => Some(s"$n = $e")
        case _                  => None
      }
      if args.isEmpty then sb ++= s"""_sb ++= $name().body; """
      else sb ++= s"""_sb ++= $name($name.Props(${ args.mkString(", ") })).body; """

    case TemplateNode.InlineTemplate(nested) =>
      sb ++= "_sb ++= "
      sb ++= htmlNodesToStringExprFromParts(nested, scopeId)
      sb ++= "; "

    case _ =>
      ()

  /** Helper: convert a nested `List[InlineTemplatePart]` (recursive case). */
  private def htmlNodesToStringExprFromParts(
    parts:   List[InlineTemplatePart],
    scopeId: String
  ): String =
    val sb = new StringBuilder
    sb ++= "{ val _sb = new StringBuilder; "
    parts.foreach {
      case InlineTemplatePart.Code(code) =>
        sb ++= code
      case InlineTemplatePart.Html(nodes) =>
        nodes.foreach(n => appendNodeToSb(n, sb, scopeId))
    }
    sb ++= "_sb.toString }"
    sb.toString

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
    node:    TemplateNode,
    buf:     StringBuilder,
    indent:  Int,
    scopeId: String
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
          children.foreach(c => emitHeadNode(c, buf, indent, scopeId))
          buf ++= s"""${ pad }renderer.head.push("</$tag>")\n"""

      case TemplateNode.Text(content) =>
        buf ++= s"""${ pad }renderer.head.push("${ escapeString(htmlEscapeLiteral(content)) }")\n"""

      case TemplateNode.Expression(code) =>
        buf ++= s"${ pad }renderer.head.push(Escape.html($code))\n"

      case _ =>
        ()

  private def renderParams(propsType: Option[String]): String =
    propsType match
      case Some(tpe) => s"props: $tpe = $tpe()"
      case None      => ""

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
