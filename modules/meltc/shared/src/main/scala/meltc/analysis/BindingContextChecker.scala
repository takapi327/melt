/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import scala.collection.mutable

import meltc.ast.*
import meltc.CompileError

/** Compile-time validator that ensures binding directives are used on
  * elements that actually support the underlying DOM property.
  *
  * Currently checks:
  *  - Media bindings (`bind:currentTime`, `bind:duration`, `bind:paused`,
  *    `bind:volume`, `bind:muted`, `bind:playbackRate`, `bind:seeking`,
  *    `bind:ended`, `bind:readyState`) — only valid on `<video>` or `<audio>`.
  *  - Video-only bindings (`bind:videoWidth`, `bind:videoHeight`) — only valid
  *    on `<video>`.
  *
  * Using these directives on other elements compiles without error today but
  * throws a `ClassCastException` at runtime; this checker surfaces the problem
  * at compile time instead.
  */
object BindingContextChecker:

  private val mediaBindings: Set[String] = Set(
    "currentTime",
    "duration",
    "paused",
    "volume",
    "muted",
    "playbackRate",
    "seeking",
    "ended",
    "readyState"
  )

  private val videoOnlyBindings: Set[String] = Set("videoWidth", "videoHeight")

  def check(ast: MeltFile, filename: String): List[CompileError] =
    val errors = mutable.ListBuffer.empty[CompileError]
    ast.template.foreach(node => walk(node, errors, filename))
    errors.toList

  private def walk(
    node:     TemplateNode,
    errors:   mutable.ListBuffer[CompileError],
    filename: String
  ): Unit = node match
    case TemplateNode.Element(tag, attrs, children) =>
      val lowerTag = tag.toLowerCase
      attrs.foreach {
        case Attr.Directive("bind", name, _, _) if mediaBindings.contains(name) =>
          if lowerTag != "video" && lowerTag != "audio" then
            errors += CompileError(
              message  = s"`bind:$name` is only valid on <video> or <audio> elements, but was used on <$tag>.",
              line     = 0,
              column   = 0,
              filename = filename
            )
        case Attr.Directive("bind", name, _, _) if videoOnlyBindings.contains(name) =>
          if lowerTag != "video" then
            errors += CompileError(
              message  = s"`bind:$name` is only valid on <video> elements, but was used on <$tag>.",
              line     = 0,
              column   = 0,
              filename = filename
            )
        case _ => ()
      }
      children.foreach(c => walk(c, errors, filename))

    case TemplateNode.Component(_, _, children) =>
      children.foreach(c => walk(c, errors, filename))

    case TemplateNode.Head(children) =>
      children.foreach(c => walk(c, errors, filename))

    case TemplateNode.InlineTemplate(parts) =>
      parts.foreach {
        case InlineTemplatePart.Html(nodes) => nodes.foreach(n => walk(n, errors, filename))
        case _                              => ()
      }

    case _ => ()
