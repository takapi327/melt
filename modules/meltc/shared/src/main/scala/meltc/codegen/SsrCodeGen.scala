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

  // ── Public API ─────────────────────────────────────────────────────────

  def scopeIdFor(objectName: String): String =
    // Mirrors SpaCodeGen so the same .melt file produces matching scope
    // IDs on both platforms — essential for hydration in Phase C.
    SpaCodeGen.scopeIdFor(objectName)

  def generate(
    ast:        MeltFile,
    objectName: String,
    pkg:        String,
    scopeId:    String
  ): String =
    val buf = new StringBuilder
    if pkg.nonEmpty then buf ++= s"package $pkg\n\n"

    buf ++= "import melt.runtime.*\n"
    buf ++= "import melt.runtime.ssr.*\n\n"

    buf ++= s"object $objectName {\n\n"
    buf ++= s"""  private val _scopeId = "$scopeId"\n\n"""

    // ── CSS ──────────────────────────────────────────────────────────────
    val hasCss = ast.style.isDefined
    ast.style.foreach { s =>
      val scoped = CssScoper.scope(s.css, scopeId)
      buf ++= s"""  private val _css =\n    "${ escapeString(scoped) }"\n\n"""
    }

    val propsType = ast.script.flatMap(_.propsType)

    // ── Emit user's Props case class / type aliases from <script> at object level ──
    // The existing <script> body is copied verbatim inside apply(), but
    // Props must be visible at the object level so callers can write
    // `Counter(Counter.Props(...))`. We extract the Props declaration by
    // scanning the script text for the `case class Props` line and emit
    // it both at object level and inside apply().

    // Extract Props-related declarations (best-effort — Phase A uses the
    // same heuristic as SpaCodeGen: copy the entire script verbatim into
    // the apply method so user declarations are in scope).
    val scriptBody = ast.script.map(_.code.trim).getOrElse("")

    // Emit Props case class at object level if present, so external callers
    // can reference `ComponentName.Props(...)`.
    val objectLevelProps = extractPropsDecl(scriptBody, propsType)
    objectLevelProps.foreach { decl =>
      buf ++= "  " + decl + "\n\n"
    }

    // ── apply() method ──────────────────────────────────────────────────
    // Components expose a single `apply` entry point so that user code can
    // call them like functions: `Counter(Counter.Props(0))` or `App()` when
    // there is no Props type.
    buf ++= s"  def apply(${ renderParams(propsType) }): RenderResult = {\n"
    buf ++= "    val renderer = SsrRenderer()\n"
    val moduleId = kebabCase(objectName)
    buf ++= s"""    renderer.trackComponent("$moduleId")\n"""
    if hasCss then buf ++= "    renderer.css.add(_scopeId, _css)\n"
    buf ++= "\n"

    // ── Script body (excluding the Props declaration already emitted) ──
    val scriptWithoutProps = stripPropsDecl(scriptBody, propsType)
    if scriptWithoutProps.nonEmpty then
      buf ++= "    // ── User script section ──\n"
      scriptWithoutProps.linesIterator.foreach { line =>
        if line.trim.isEmpty then buf ++= "\n"
        else buf ++= s"    $line\n"
      }
      buf ++= "\n"

    // ── Template nodes ────────────────────────────────────────────────────
    buf ++= "    // ── Template ──\n"
    ast.template.foreach(node => emitNode(node, buf, indent = 4, scopeId))

    buf ++= "\n    renderer.result()\n"
    buf ++= "  }\n"
    buf ++= "}\n"

    buf.toString

  // ── Node emission ──────────────────────────────────────────────────────

  private def emitNode(
    node:    TemplateNode,
    buf:     StringBuilder,
    indent:  Int,
    scopeId: String
  ): Unit =
    val pad = " " * indent
    node match
      case TemplateNode.Element(tag, attrs, children) =>
        emitElementStart(tag, attrs, buf, indent, scopeId)
        if HtmlVoidElements.isVoid(tag) then
          // Void elements end with "/>", no children.
          buf ++= s"""${ pad }renderer.push(">")\n"""
        else
          buf ++= s"""${ pad }renderer.push(">")\n"""
          children.foreach(c => emitNode(c, buf, indent, scopeId))
          buf ++= s"""${ pad }renderer.push("</$tag>")\n"""

      case TemplateNode.Text(content) =>
        // Text content is compile-time static, so we escape it once here
        // rather than at runtime. This matches Svelte 5's behaviour.
        val escaped = htmlEscapeLiteral(content)
        buf ++= s"""${ pad }renderer.push("${ escapeString(escaped) }")\n"""

      case TemplateNode.Expression(code) =>
        buf ++= s"$pad renderer.push(Escape.html($code))\n".substring(1)

      case TemplateNode.Component(name, attrs, _) =>
        emitComponentCall(name, attrs, buf, indent)

      case TemplateNode.Head(children) =>
        // <melt:head> — route children through renderer.head
        children.foreach(c => emitHeadNode(c, buf, indent, scopeId))

      case TemplateNode.Window(_) | TemplateNode.Body(_) =>
        // Window / body event handlers have no meaning in SSR.
        ()

      case TemplateNode.InlineTemplate(_) | TemplateNode.DynamicElement(_, _, _) =>
        // Phase B: list rendering / dynamic elements.
        buf ++= s"${ pad }// TODO(SSR Phase B): InlineTemplate / DynamicElement\n"

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
    val pad = " " * indent
    // Find an existing static class attribute to combine with the scope id.
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
      // Static class is folded into emitScopedClassAttr — skip here.
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
        else
          buf ++= s"""${ pad }renderer.push(s\" $name=\\\"\" + Escape.attr($expr) + \"\\\"\")\n"""

      case Attr.Shorthand(varName) =>
        // `<input {value}>` → `value={value}` — treat as dynamic.
        if UrlAttributesForCodegen.isUrlAttribute(tag, varName) then
          buf ++= s"""${ pad }renderer.push(s\" $varName=\\\"\" + Escape.url($varName) + \"\\\"\")\n"""
        else
          buf ++= s"""${ pad }renderer.push(s\" $varName=\\\"\" + Escape.attr($varName) + \"\\\"\")\n"""

      case Attr.Spread(expr) =>
        buf ++= s"""${ pad }renderer.spreadAttrs("$tag", $expr)\n"""

      case Attr.EventHandler(_, _) =>
        // Event handlers are stripped in SSR.
        ()

      case Attr.Directive(kind, name, expr, _) =>
        kind match
          case "bind" =>
            // Phase A: bind:value={v} on a normal input → emit as a plain
            // value attribute. Rich <textarea> / <select> handling arrives
            // in Phase B (§12.3.6).
            expr.foreach { e =>
              buf ++= s"""${ pad }renderer.push(s\" $name=\\\"\" + Escape.attr($e) + \"\\\"\")\n"""
            }
          case "class" =>
            // class:active={flag} → conditionally append class name.
            // Phase A simplified: emit as additional class attribute through
            // a runtime helper.
            expr.foreach { e =>
              buf ++= s"""${ pad }if ($e) then renderer.push(" $name")\n"""
            }
          case "style" =>
            expr.foreach { e =>
              buf ++= s"""${ pad }renderer.push(s\" style=\\\"$name:\" + Escape.attr($e) + \";\\\"\")\n"""
            }
          case _ =>
            // use:, transition:, in:, out:, animate: — ignored in SSR.
            ()

  /** Emits a `<Child ... />` component invocation. */
  private def emitComponentCall(
    name:   String,
    attrs:  List[Attr],
    buf:    StringBuilder,
    indent: Int
  ): Unit =
    val pad = " " * indent
    // Build a Props(...) call from attribute bindings. In Phase A we only
    // support Shorthand, Static, Dynamic, and Spread passed to components.
    val args = attrs.flatMap {
      case Attr.Shorthand(n)   => Some(s"$n = $n")
      case Attr.Static(n, v)   => Some(s"""$n = \"${ escapeString(v) }\"""")
      case Attr.Dynamic(n, e)  => Some(s"$n = $e")
      case Attr.Spread(_)      => None // spread to component not yet supported
      case _                   => None
    }

    if args.isEmpty then
      buf ++= s"${ pad }renderer.merge($name())\n"
    else
      buf ++= s"${ pad }renderer.merge($name($name.Props(${ args.mkString(", ") })))\n"

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
        // Single dynamic or static title: collapse children into one string.
        titleChildren match
          case TemplateNode.Expression(code) :: Nil =>
            buf ++= s"${ pad }renderer.head.title($code)\n"
          case _ =>
            val staticText = titleChildren.collect {
              case TemplateNode.Text(t) => t
            }.mkString
            buf ++= s"""${ pad }renderer.head.push("<title>${ escapeString(htmlEscapeLiteral(staticText)) }</title>")\n"""

      case TemplateNode.Element(tag, attrs, children) =>
        // For arbitrary head children, emit their tag open / attrs / close
        // into the head buffer using a helper.
        buf ++= s"""${ pad }renderer.head.push("<$tag")\n"""
        attrs.foreach {
          case Attr.Static(n, v) =>
            val lit = escapeString(s""" $n=\"${ htmlAttrEscapeLiteral(v) }\"""")
            buf ++= s"""${ pad }renderer.head.push("$lit")\n"""
          case Attr.Dynamic(n, e) =>
            buf ++= s"""${ pad }renderer.head.push(s\" $n=\\\"\" + Escape.attr($e) + \"\\\"\")\n"""
          case _ => ()
        }
        if HtmlVoidElements.isVoid(tag) then
          buf ++= s"""${ pad }renderer.head.push(">")\n"""
        else
          buf ++= s"""${ pad }renderer.head.push(">")\n"""
          children.foreach(c => emitHeadNode(c, buf, indent, scopeId))
          buf ++= s"""${ pad }renderer.head.push("</$tag>")\n"""

      case TemplateNode.Text(content) =>
        buf ++= s"""${ pad }renderer.head.push("${ escapeString(htmlEscapeLiteral(content)) }")\n"""

      case TemplateNode.Expression(code) =>
        buf ++= s"${ pad }renderer.head.push(Escape.html($code))\n"

      case _ =>
        // Other node kinds inside <melt:head> are skipped in Phase A.
        ()

  // ── Utilities ──────────────────────────────────────────────────────────

  private def renderParams(propsType: Option[String]): String =
    propsType match
      case Some(tpe) => s"props: $tpe = $tpe()"
      case None      => ""

  /** Converts `objectName` to kebab-case for Vite `moduleID`.
    * `Counter` → `counter`, `TodoList` → `todo-list`.
    */
  private def kebabCase(s: String): String =
    val buf = new StringBuilder(s.length + 4)
    s.zipWithIndex.foreach { case (c, i) =>
      if c.isUpper then
        if i > 0 then buf += '-'
        buf += c.toLower
      else
        buf += c
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

  /** Phase A best-effort extraction of `case class Props(...)` /
    * `type Props = ...` declarations from the user's script body so that
    * they can be hoisted to the object level.
    */
  private def extractPropsDecl(script: String, propsType: Option[String]): Option[String] =
    if propsType.isEmpty || script.isEmpty then None
    else
      val tpe = propsType.get
      // Look for a line that begins a `case class Props(` or `type Props =`
      // and copy from that line up through the matching closing `)` or EOL.
      val lines = script.linesIterator.toVector
      val start = lines.indexWhere { l =>
        val trimmed = l.trim
        trimmed.startsWith(s"case class $tpe") || trimmed.startsWith(s"type $tpe")
      }
      if start < 0 then None
      else
        // Heuristic: assume the declaration fits on one physical line (the
        // common case for Props). Multi-line case classes are left inline.
        Some(lines(start).trim)

  /** Removes the Props declaration from the script body so it is not
    * duplicated inside `render()`. Falls back to the original body if
    * extraction fails.
    */
  private def stripPropsDecl(script: String, propsType: Option[String]): String =
    if propsType.isEmpty || script.isEmpty then script
    else
      val tpe = propsType.get
      val lines = script.linesIterator.toVector
      val withoutProps = lines.filterNot { l =>
        val t = l.trim
        t.startsWith(s"case class $tpe") || t.startsWith(s"type $tpe")
      }
      withoutProps.mkString("\n")

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
