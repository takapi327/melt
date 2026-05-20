/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

import meltc.ast.TemplateNode

/** Immutable map from [[TemplateNode]] identity to [[SourceSpan]].
  *
  * Uses a [[java.util.IdentityHashMap]] internally so that two structurally
  * equal nodes (e.g. `Expression("x")` appearing twice in the template) are
  * treated as distinct entries.  This is essential because Scala enum case
  * members implement structural `equals`/`hashCode`, making a regular
  * `Map[TemplateNode, SourceSpan]` unsuitable.
  *
  * Obtain an instance via [[NodePositions.Builder]].
  */
final class NodePositions private (private val map: java.util.IdentityHashMap[TemplateNode, SourceSpan]):

  /** Returns the [[SourceSpan]] recorded for `node`, or [[SourceSpan.unknown]]
    * if no position was recorded (e.g. synthetic nodes).
    */
  def spanOf(node: TemplateNode): SourceSpan =
    val span = map.get(node)
    if span eq null then SourceSpan.unknown else span

object NodePositions:

  /** An empty [[NodePositions]] with no recorded positions. */
  val empty: NodePositions = new NodePositions(new java.util.IdentityHashMap())

  /** Mutable builder — filled by [[meltc.parser.TemplateParser]] during parsing. */
  final class Builder:
    private val map = new java.util.IdentityHashMap[TemplateNode, SourceSpan]()

    /** Records `span` for `node`. Overwrites any previously recorded span. */
    def add(node: TemplateNode, span: SourceSpan): Unit = map.put(node, span)

    /** Produces an immutable [[NodePositions]] from the accumulated entries. */
    def result(): NodePositions = new NodePositions(map)
