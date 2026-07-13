/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms.codec

/** Symmetric [[FieldEncoder]] + [[FieldDecoder]] for a form field type.
  *
  * Defining a single `FieldCodec[A]` is enough to make `A` usable both as a
  * decoded form field (server) and as a seeded `<input value>` (client) — the two
  * stay in sync by construction. Compose one from an existing codec with
  * [[imap]] / [[eimap]]:
  * {{{
  * enum Role:
  *   case Admin, User
  *
  * given FieldCodec[Role] = FieldCodec[String].eimap {
  *   case "admin" => Right(Role.Admin)
  *   case "user"  => Right(Role.User)
  *   case other   => Left(s"invalid role: $other")
  * }(_.toString.toLowerCase)
  * }}}
  */
trait FieldCodec[A] extends FieldEncoder[A], FieldDecoder[A]:
  self =>

  /** Bimap to a total new type `B` (encode with `g`, decode with `f`). */
  def imap[B](f: A => B)(g: B => A): FieldCodec[B] =
    FieldCodec.from((n, vs) => self.decode(n, vs).map(f))(b => self.encode(g(b)))

  /** Bimap where decoding may fail. */
  def eimap[B](f: A => Either[String, B])(g: B => A): FieldCodec[B] =
    FieldCodec.from((n, vs) => self.decode(n, vs).flatMap(f))(b => self.encode(g(b)))

  /** Encodes then decodes a value — a codec-law check for tests. Succeeds with the
    * original value when the two directions agree (`decode(encode(a)) == Right(a)`).
    */
  def roundTrip(value: A): Either[String, A] = decode("field", encode(value))

object FieldCodec:

  def apply[A](using c: FieldCodec[A]): FieldCodec[A] = c

  /** Builds a codec from a decode and an encode function. */
  def from[A](dec: (String, List[String]) => Either[String, A])(enc: A => List[String]): FieldCodec[A] =
    new FieldCodec[A]:
      def decode(name:  String, values: List[String]): Either[String, A] = dec(name, values)
      def encode(value: A):                            List[String]      = enc(value)

  /** A required scalar field: decode fails when absent or malformed. */
  private def scalar[A](tpe: String)(parse: String => Option[A])(render: A => String): FieldCodec[A] =
    from { (name, values) =>
      values.headOption match
        case None    => Left(s"Missing required field: $name")
        case Some(v) => parse(v).toRight(s"Field '$name' is not a valid $tpe: $v")
    }(a => List(render(a)))

  given FieldCodec[String] =
    from((name, values) => values.headOption.toRight(s"Missing required field: $name"))(s => List(s))

  given FieldCodec[Int]    = scalar("integer")(_.toIntOption)(_.toString)
  given FieldCodec[Long]   = scalar("long")(_.toLongOption)(_.toString)
  given FieldCodec[Double] = scalar("number")(_.toDoubleOption)(_.toString)

  given FieldCodec[Boolean] =
    from { (name, values) =>
      values.headOption match
        case None                                  => Right(false) // absent checkbox
        case Some("true") | Some("1") | Some("on") => Right(true)  // "on" = default checkbox value
        case Some("false") | Some("0") | Some("")  => Right(false)
        case Some(v)                               => Left(s"Field '$name' is not a valid boolean: $v")
    }(b => List(b.toString))

  /** Optional field: absent decodes to `None`, `None` encodes to no value. */
  given [A](using inner: FieldCodec[A]): FieldCodec[Option[A]] =
    from((name, values) => if values.isEmpty then Right(None) else inner.decode(name, values).map(Some(_))) {
      case Some(a) => inner.encode(a)
      case None    => Nil
    }

  /** Multi-valued field (`<select multiple>`, repeated inputs). */
  given [A](using inner: FieldCodec[A]): FieldCodec[List[A]] =
    from { (name, values) =>
      values.foldLeft[Either[String, List[A]]](Right(Nil)) { (acc, v) =>
        for
          xs <- acc
          x  <- inner.decode(name, List(v))
        yield xs :+ x
      }
    }(as => as.flatMap(inner.encode))
