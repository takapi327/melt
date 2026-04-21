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
  private[meltkit] def paramDecoders: List[(String, PathParamDecoder[?])]

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

  private final case class Impl[P <: AnyNamedTuple](
    segments:      List[PathSegment],
    paramDecoders: List[(String, PathParamDecoder[?])]
  ) extends PathSpec[P]

  private[meltkit] def of[P <: AnyNamedTuple](
    segments:      List[PathSegment],
    paramDecoders: List[(String, PathParamDecoder[?])] = Nil
  ): PathSpec[P] = Impl(segments, paramDecoders)

  /** Splits a string on `/` to produce static path segments.
    *
    * `"api/users"` → `[Static("api"), Static("users")]`
    */
  private[meltkit] def staticSegments(s: String): List[PathSegment] =
    s.split('/').filter(_.nonEmpty).toList.map(PathSegment.Static(_))

  /** Converts a plain `String` to a no-param `PathSpec[Empty]`.
    *
    * Slashes in `s` produce multiple static segments, so `"api/users"` is
    * equivalent to the path `/api/users`.
    */
  private[meltkit] def fromString(s: String): PathSpec[Empty] =
    of(staticSegments(s))

  extension [P <: AnyNamedTuple](spec: PathSpec[P])

    /** `spec / id` or `spec / "posts"` — appends a dynamic or static segment.
      *
      * A single transparent-inline method eliminates overload ambiguity while
      * preserving the full NamedTuple type via the `AppendedWith` match type.
      */
    transparent inline def /[T](t: T): PathSpec[AppendedWith[P, T]] =
      inline t match
        case p: PathParam[?, ?] =>
          of(
            spec.segments :+ PathSegment.Param(p.paramName),
            spec.paramDecoders :+ (p.paramName -> p.decoder)
          ).asInstanceOf[PathSpec[AppendedWith[P, T]]]
        case s: String =>
          of(
            spec.segments ++ staticSegments(s),
            spec.paramDecoders
          ).asInstanceOf[PathSpec[AppendedWith[P, T]]]

// ── String-receiver DSL ──────────────────────────────────────────────────────

extension (s: String)

  /** `"users" / id` — starts a PathSpec with a dynamic segment.
    *
    * Slashes in `s` produce multiple leading static segments.
    */
  def /[N <: String, A](p: PathParam[N, A]): PathSpec[NT[N *: EmptyTuple, A *: EmptyTuple]] =
    PathSpec.of(
      PathSpec.staticSegments(s) :+ PathSegment.Param(p.paramName),
      List(p.paramName -> p.decoder)
    )

  /** `"api" / otherSpec` — prepends static segment(s) to an existing PathSpec.
    *
    * Slashes in `s` produce multiple leading static segments.
    */
  def /[P <: AnyNamedTuple](spec: PathSpec[P]): PathSpec[P] =
    PathSpec.of(PathSpec.staticSegments(s) ++ spec.segments, spec.paramDecoders)
