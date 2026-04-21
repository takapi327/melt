/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple
import scala.NamedTuple.Concat
import scala.NamedTuple.NamedTuple as NT

/** A type-safe URL path pattern.
  *
  * `P` is a [[scala.NamedTuple]] whose fields correspond to the dynamic
  * segments of the path in order of appearance.
  *
  * Build path specs with the `/` DSL:
  *
  * {{{
  * val id     = param[Int]("id")
  * val postId = param[String]("postId")
  *
  * "users" / id               // PathSpec[(id: Int)]
  * "users" / id / "posts"     // PathSpec[(id: Int)]
  * "users" / id / "posts" / postId  // PathSpec[(id: Int, postId: String)]
  * }}}
  */
sealed trait PathSpec[P <: AnyNamedTuple]:
  def segments: List[PathSegment]

object PathSpec:

  /** The NamedTuple type for a path with no dynamic parameters. */
  type Empty = NT[EmptyTuple, EmptyTuple]

  private final case class Impl[P <: AnyNamedTuple](segments: List[PathSegment]) extends PathSpec[P]

  private[meltkit] def of[P <: AnyNamedTuple](segments: List[PathSegment]): PathSpec[P] = Impl(segments)

  /** Allows a plain `String` to be used wherever a `PathSpec[Empty]` is expected.
    *
    * {{{
    * app.get("users") { ctx => ... }          // String → PathSpec[Empty]
    * app.get("api" / "users" / id) { ... }   // PathSpec[(id: Int)]
    * }}}
    */
  given Conversion[String, PathSpec[Empty]] = s =>
    of(if s.isEmpty then Nil else List(PathSegment.Static(s)))

// ── Path-building DSL ────────────────────────────────────────────────────────

extension (s: String)

  /** `"users" / id` — starts a PathSpec with one dynamic segment. */
  def /[N <: String, A](p: PathParam[N, A]): PathSpec[NT[N *: EmptyTuple, A *: EmptyTuple]] =
    PathSpec.of(List(PathSegment.Static(s), PathSegment.Param(p.paramName)))

  /** `"api" / "users"` — prepends a static segment to an existing PathSpec. */
  def /[P <: AnyNamedTuple](spec: PathSpec[P]): PathSpec[P] =
    PathSpec.of(PathSegment.Static(s) :: spec.segments)

extension [P <: AnyNamedTuple](spec: PathSpec[P])

  /** `spec / id` — appends a dynamic segment, accumulating its type into P. */
  @scala.annotation.targetName("appendParam")
  def /[N <: String, A](p: PathParam[N, A])
    : PathSpec[Concat[P, NT[N *: EmptyTuple, A *: EmptyTuple]]] =
    PathSpec.of(spec.segments :+ PathSegment.Param(p.paramName))

  /** `spec / "posts"` — appends a static segment, preserving P unchanged. */
  def /(s: String): PathSpec[P] =
    PathSpec.of(spec.segments :+ PathSegment.Static(s))
