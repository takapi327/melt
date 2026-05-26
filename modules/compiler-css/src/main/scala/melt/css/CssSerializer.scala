/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

/** CSS AST を文字列にシリアライズする。 */
object CssSerializer:

  def serialize(nodes: List[CssNode]): String =
    val sb = new StringBuilder
    nodes.foreach(serializeNode(_, sb, indent = 0))
    sb.toString

  private def serializeNode(node: CssNode, sb: StringBuilder, indent: Int): Unit =
    val pad = "  " * indent
    node match
      case CssNode.Comment(text) =>
        sb ++= pad ++= text += '\n'

      case CssNode.RawText(text) =>
        text.linesIterator.foreach { line =>
          val t = line.trim
          if t.nonEmpty then sb ++= pad ++= t += '\n'
        }

      case CssNode.StyleRule(selector, body) =>
        sb ++= pad ++= selector ++= " {\n"
        body.foreach(serializeNode(_, sb, indent + 1))
        sb ++= pad += '}' += '\n'

      case CssNode.AtRule(name, prelude, None) =>
        if prelude.isEmpty then sb ++= pad += '@' ++= name += ';' += '\n'
        else sb ++= pad += '@' ++= name += ' ' ++= prelude += ';' += '\n'

      case CssNode.AtRule(name, prelude, Some(body)) =>
        if prelude.isEmpty then sb ++= pad += '@' ++= name ++= " {\n"
        else sb ++= pad += '@' ++= name += ' ' ++= prelude ++= " {\n"
        body.foreach(serializeNode(_, sb, indent + 1))
        sb ++= pad += '}' += '\n'
