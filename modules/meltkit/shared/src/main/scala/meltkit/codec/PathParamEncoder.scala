/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

/** Encodes a value of type `A` into a URL path-segment string.
  *
  * This is the inverse of [[PathParamDecoder]]. Built-in instances for
  * `String`, `Int`, and `Long` are provided via [[PathParamCodec]], which
  * extends both this trait and [[PathParamDecoder]].
  *
  * == Combinators ==
  *
  * {{{
  * // contramap — adapt from a wrapper type
  * given PathParamEncoder[UserId] = PathParamEncoder[Int].contramap(_.value)
  * }}}
  */
trait PathParamEncoder[A]:
  def encode(value: A): String

  /** Adapts this encoder to a type `B` by first applying `f` to convert `B → A`. */
  def contramap[B](f: B => A): PathParamEncoder[B] =
    value => encode(f(value))
