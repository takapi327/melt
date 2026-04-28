/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple
import scala.NamedTuple.Concat
import scala.NamedTuple.NamedTuple as NT

import meltkit.codec.PathParamDecoder
import meltkit.codec.PathParamEncoder

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
  def segments:                       List[PathSegment]
  private[meltkit] def paramDecoders: List[(String, PathParamDecoder[?])]
  private[meltkit] def paramEncoders: List[(String, PathParamEncoder[?])]

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

  /** The canonical empty-params value of type [[Empty]].
    *
    * `NamedTuple` is an `opaque type`, so outside its defining scope the
    * compiler cannot prove that `EmptyTuple <: Empty` without a cast.
    * The cast is safe because `NamedTuple[N, V]` erases to `V` at runtime,
    * making `NamedTuple[EmptyTuple, EmptyTuple]` and `EmptyTuple` identical
    * at the JVM / JS level.
    *
    * All internal code that needs an empty parameter tuple should use this
    * value instead of writing `EmptyTuple.asInstanceOf[NamedTuple.Empty]`
    * directly.
    */
  private[meltkit] val emptyValue: Empty = EmptyTuple.asInstanceOf[Empty]

  private final case class Impl[P <: AnyNamedTuple](
    segments:      List[PathSegment],
    paramDecoders: List[(String, PathParamDecoder[?])],
    paramEncoders: List[(String, PathParamEncoder[?])]
  ) extends PathSpec[P]

  private[meltkit] def of[P <: AnyNamedTuple](
    segments:      List[PathSegment],
    paramDecoders: List[(String, PathParamDecoder[?])] = Nil,
    paramEncoders: List[(String, PathParamEncoder[?])] = Nil
  ): PathSpec[P] = Impl(segments, paramDecoders, paramEncoders)

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
            spec.paramDecoders :+ (p.paramName -> p.decoder),
            spec.paramEncoders :+ (p.paramName -> p.encoder)
          ).asInstanceOf[PathSpec[AppendedWith[P, T]]]
        case s: String =>
          of(
            spec.segments ++ staticSegments(s),
            spec.paramDecoders,
            spec.paramEncoders
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
      List(p.paramName -> p.decoder),
      List(p.paramName -> p.encoder)
    )

  /** `"api" / otherSpec` — prepends static segment(s) to an existing PathSpec.
    *
    * Slashes in `s` produce multiple leading static segments.
    */
  def /[P <: AnyNamedTuple](spec: PathSpec[P]): PathSpec[P] =
    PathSpec.of(PathSpec.staticSegments(s) ++ spec.segments, spec.paramDecoders, spec.paramEncoders)
