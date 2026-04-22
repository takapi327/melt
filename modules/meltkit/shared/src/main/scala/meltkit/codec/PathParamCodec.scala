/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.codec

/** Combines [[PathParamDecoder]] and [[PathParamEncoder]] for a type `A`.
  *
  * Provides bidirectional conversion between URL path-segment strings and
  * typed values.  Built-in instances are provided for `String`, `Int`, and
  * `Long` in the companion object.
  *
  * Because `PathParamCodec[A]` extends both [[PathParamDecoder]][A] and
  * [[PathParamEncoder]][A], a `given PathParamCodec[A]` satisfies both
  * constraints automatically (e.g. for [[meltkit.param]]).
  *
  * == Creating a PathParamCodec ==
  *
  * {{{
  * // From explicit decoder and encoder:
  * val uuidCodec: PathParamCodec[UUID] = PathParamCodec.from(
  *   s => Try(UUID.fromString(s)).toEither.left.map(_.getMessage),
  *   _.toString
  * )
  *
  * // Bidirectional map from an existing codec:
  * given PathParamCodec[UserId] = PathParamCodec[Int].imap(UserId(_))(_.value)
  * }}}
  */
trait PathParamCodec[A] extends PathParamDecoder[A] with PathParamEncoder[A]:

  /** Bidirectional mapping — transforms both decode and encode directions. */
  def imap[B](f: A => B)(g: B => A): PathParamCodec[B] =
    PathParamCodec.from(this.map(f), this.contramap(g))

object PathParamCodec:

  /** Creates a [[PathParamCodec]] from explicit [[PathParamDecoder]] and [[PathParamEncoder]]. */
  def from[A](dec: PathParamDecoder[A], enc: PathParamEncoder[A]): PathParamCodec[A] =
    new PathParamCodec[A]:
      def decode(s: String): Either[String, A] = dec.decode(s)
      def encode(value: A): String             = enc.encode(value)

  /** Derives a [[PathParamCodec]] from given [[PathParamDecoder]] and [[PathParamEncoder]] in scope. */
  def of[A](using dec: PathParamDecoder[A], enc: PathParamEncoder[A]): PathParamCodec[A] =
    from(dec, enc)

  given PathParamCodec[String] with
    def decode(s: String): Either[String, String] = Right(s)
    def encode(value: String): String             = value

  given PathParamCodec[Int] with
    def decode(s: String): Either[String, Int] =
      s.toIntOption.toRight(s"'$s' is not a valid Int")
    def encode(value: Int): String = value.toString

  given PathParamCodec[Long] with
    def decode(s: String): Either[String, Long] =
      s.toLongOption.toRight(s"'$s' is not a valid Long")
    def encode(value: Long): String = value.toString
