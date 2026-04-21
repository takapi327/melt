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
  * "users" / id                      // PathSpec[(id: Int)]
  * "users" / id / "posts"            // PathSpec[(id: Int)]
  * "users" / id / "posts" / postId   // PathSpec[(id: Int, postId: String)]
  * }}}
  *
  */
sealed trait PathSpec[P <: AnyNamedTuple]:
  def segments: List[PathSegment]

/** Computes the NamedTuple type after appending `T` to `PathSpec[P]`.
  *
  *   - `String`          → P unchanged (static segment)
  *   - `PathParam[n, a]` → Concat[P, (n: a)] (dynamic segment)
  */
private type AppendedWith[P <: AnyNamedTuple, T] <: AnyNamedTuple = T match
  case String          => P
  case PathParam[n, a] => Concat[P, NT[n *: EmptyTuple, a *: EmptyTuple]]

object PathSpec:

  /** The NamedTuple type for a path with no dynamic parameters. */
  type Empty = NT[EmptyTuple, EmptyTuple]

  private final case class Impl[P <: AnyNamedTuple](segments: List[PathSegment]) extends PathSpec[P]

  private[meltkit] def of[P <: AnyNamedTuple](segments: List[PathSegment]): PathSpec[P] = Impl(segments)

  /** Converts a plain `String` to a no-param `PathSpec[Empty]`. */
  private[meltkit] def fromString(s: String): PathSpec[Empty] =
    of(if s.isEmpty then Nil else List(PathSegment.Static(s)))

  extension [P <: AnyNamedTuple](spec: PathSpec[P])

    /** `spec / id` or `spec / "posts"` — appends a dynamic or static segment.
      *
      * A single transparent-inline method eliminates overload ambiguity while
      * preserving the full NamedTuple type via the `AppendedWith` match type.
      */
    transparent inline def /[T](t: T): PathSpec[AppendedWith[P, T]] =
      inline t match
        case p: PathParam[?, ?] =>
          of(spec.segments :+ PathSegment.Param(p.paramName))
            .asInstanceOf[PathSpec[AppendedWith[P, T]]]
        case s: String =>
          of(spec.segments :+ PathSegment.Static(s))
            .asInstanceOf[PathSpec[AppendedWith[P, T]]]

// ── String-receiver DSL ──────────────────────────────────────────────────────

extension (s: String)

  /** `"users" / id` — starts a PathSpec with a dynamic segment. */
  def /[N <: String, A](p: PathParam[N, A]): PathSpec[NT[N *: EmptyTuple, A *: EmptyTuple]] =
    PathSpec.of(List(PathSegment.Static(s), PathSegment.Param(p.paramName)))

  /** `"api" / otherSpec` — prepends a static segment to an existing PathSpec. */
  def /[P <: AnyNamedTuple](spec: PathSpec[P]): PathSpec[P] =
    PathSpec.of(PathSegment.Static(s) :: spec.segments)
