/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import org.scalajs.dom

/** Produces an indented, human-readable HTML representation of a DOM element.
  *
  * The output is intentionally similar in structure to `prettyDOM` from
  * `@testing-library/dom`, but is implemented entirely in Scala.js to avoid
  * CommonJS module-system constraints imposed by `JSDOMNodeJSEnv`.
  */
private[testkit] object PrettyDom:

  private val IndentSize = 2

  /** Returns a pretty-printed HTML string for `node`. */
  def apply(node: dom.Node): String =
    render(node, 0).mkString("\n")

  // Node type constants (https://developer.mozilla.org/en-US/docs/Web/API/Node/nodeType)
  private val TextNode    = 3
  private val ElementNode = 1

  private def render(node: dom.Node, depth: Int): List[String] =
    node.nodeType match
      case TextNode =>
        val text = node.textContent.trim
        if text.isEmpty then Nil
        else List(indent(depth) + text)

      case ElementNode =>
        val el       = node.asInstanceOf[dom.Element]
        val tag      = el.tagName.toLowerCase
        val attrs    = renderAttrs(el)
        val children = childLines(el, depth + 1)

        if children.isEmpty then
          List(s"${indent(depth)}<$tag$attrs />")
        else
          List(s"${indent(depth)}<$tag$attrs>") ++ children ++ List(s"${indent(depth)}</$tag>")

      case _ => Nil

  private def childLines(el: dom.Element, depth: Int): List[String] =
    val nodes = el.childNodes
    (0 until nodes.length).toList.flatMap(i => render(nodes(i), depth))

  private def renderAttrs(el: dom.Element): String =
    val attrs = el.attributes
    if attrs.length == 0 then ""
    else
      val parts = (0 until attrs.length).map { i =>
        val a = attrs(i)
        if a.value.isEmpty then a.name
        else s"""${a.name}="${a.value}""""
      }
      " " + parts.mkString(" ")

  private def indent(depth: Int): String = " " * (depth * IndentSize)
