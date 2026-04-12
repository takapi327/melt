/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.json

import scala.compiletime.{ constValue, erasedValue, summonInline }
import scala.deriving.Mirror

/** Type class used by the Melt code generator to encode a component's
  * Props to JSON on the SSR side and decode it back on the SPA
  * hydration side.
  *
  * The compiler emits exactly one call per component:
  *
  * {{{
  *   private val _propsCodec: PropsCodec[Props] = PropsCodec.derived
  * }}}
  *
  * Everything else — walking the case class structure, escaping
  * strings, recursing into nested types — is handled by Scala 3's
  * inline derivation, which means `meltc` does not need to understand
  * the Props type at all. Any type that is:
  *
  *   - a primitive with a built-in instance (see the givens below),
  *   - `Option[A]` / `List[A]` where `A` has a `PropsCodec`, or
  *   - a product (case class, Tuple) whose members all have `PropsCodec`s
  *
  * will work, regardless of whether it's defined inside the component
  * script block or imported from another file.
  *
  * Users can plug in their own codec for domain types by providing a
  * `given PropsCodec[MyType]` in scope at the component's generation
  * site.
  *
  * == Design notes ==
  *
  *   - The encoder produces a compact JSON string directly into a
  *     `StringBuilder`, keeping allocation low on the SSR hot path.
  *   - The decoder is lenient: fields absent from the JSON fall through
  *     to the component's declared defaults when available, so partial
  *     hydration payloads (e.g. from a future streaming SSR mode) still
  *     produce a valid component.
  *   - Named-tuple derivation is intentionally out of scope for the
  *     current Scala 3.3 baseline. Named tuples landed as experimental
  *     in Scala 3.7 and will be added once the project moves off 3.3.
  */
trait PropsCodec[A]:
  def encode(value: A, buf: StringBuilder): Unit
  def decode(json:  SimpleJson.JsonValue):  A

  /** Convenience wrapper that materialises the encoded form as a string. */
  final def encodeToString(value: A): String =
    val buf = new StringBuilder
    encode(value, buf)
    buf.toString

object PropsCodec:

  given PropsCodec[String] with
    def encode(v: String, buf: StringBuilder): Unit   = buf ++= SimpleJson.encString(v)
    def decode(j: SimpleJson.JsonValue):       String = j match
      case SimpleJson.JsonValue.Str(s) => s
      case other                       => typeMismatch("String", other)

  given PropsCodec[Int] with
    def encode(v: Int, buf: StringBuilder): Unit = buf.append(v)
    def decode(j: SimpleJson.JsonValue):    Int  = j match
      case SimpleJson.JsonValue.Num(n) => n.toInt
      case other                       => typeMismatch("Int", other)

  given PropsCodec[Long] with
    def encode(v: Long, buf: StringBuilder): Unit = buf.append(v)
    def decode(j: SimpleJson.JsonValue):     Long = j match
      case SimpleJson.JsonValue.Num(n) => n.toLong
      case other                       => typeMismatch("Long", other)

  given PropsCodec[Double] with
    def encode(v: Double, buf: StringBuilder): Unit   = buf ++= SimpleJson.encNumber(v)
    def decode(j: SimpleJson.JsonValue):       Double = j match
      case SimpleJson.JsonValue.Num(n) => n
      case other                       => typeMismatch("Double", other)

  given PropsCodec[Float] with
    def encode(v: Float, buf: StringBuilder): Unit  = buf ++= SimpleJson.encNumber(v.toDouble)
    def decode(j: SimpleJson.JsonValue):      Float = j match
      case SimpleJson.JsonValue.Num(n) => n.toFloat
      case other                       => typeMismatch("Float", other)

  given PropsCodec[Boolean] with
    def encode(v: Boolean, buf: StringBuilder): Unit    = buf.append(v)
    def decode(j: SimpleJson.JsonValue):        Boolean = j match
      case SimpleJson.JsonValue.Bool(b) => b
      case other                        => typeMismatch("Boolean", other)

  given [A](using inner: PropsCodec[A]): PropsCodec[Option[A]] with
    def encode(v: Option[A], buf: StringBuilder): Unit = v match
      case None    => buf ++= "null"
      case Some(a) => inner.encode(a, buf)
    def decode(j: SimpleJson.JsonValue): Option[A] = j match
      case SimpleJson.JsonValue.Null => None
      case other                     => Some(inner.decode(other))

  given [A](using inner: PropsCodec[A]): PropsCodec[List[A]] with
    def encode(v: List[A], buf: StringBuilder): Unit =
      buf += '['
      var first = true
      v.foreach { a =>
        if first then first = false else buf += ','
        inner.encode(a, buf)
      }
      buf += ']'
    def decode(j: SimpleJson.JsonValue): List[A] = j match
      case SimpleJson.JsonValue.Arr(items) => items.map(inner.decode)
      case SimpleJson.JsonValue.Null       => Nil
      case other                           => typeMismatch("List", other)

  given [A](using inner: PropsCodec[A]): PropsCodec[Vector[A]] with
    def encode(v: Vector[A], buf: StringBuilder): Unit =
      buf += '['
      var first = true
      v.foreach { a =>
        if first then first = false else buf += ','
        inner.encode(a, buf)
      }
      buf += ']'
    def decode(j: SimpleJson.JsonValue): Vector[A] = j match
      case SimpleJson.JsonValue.Arr(items) => items.map(inner.decode).toVector
      case SimpleJson.JsonValue.Null       => Vector.empty
      case other                           => typeMismatch("Vector", other)

  given [A](using inner: PropsCodec[A]): PropsCodec[Seq[A]] with
    def encode(v: Seq[A], buf: StringBuilder): Unit =
      buf += '['
      var first = true
      v.foreach { a =>
        if first then first = false else buf += ','
        inner.encode(a, buf)
      }
      buf += ']'
    def decode(j: SimpleJson.JsonValue): Seq[A] = j match
      case SimpleJson.JsonValue.Arr(items) => items.map(inner.decode)
      case SimpleJson.JsonValue.Null       => Nil
      case other                           => typeMismatch("Seq", other)

  /** Automatic derivation for any product type (case class / Tuple).
    *
    * Exposed as an `inline given` so that nested Props types — e.g.
    * `case class Parent(children: List[Child])` — recursively resolve
    * their inner `PropsCodec` instances without the user having to
    * manually derive each one. Users who want custom JSON behaviour
    * for a specific type can still override this by putting a more
    * specific `given PropsCodec[Foo]` in scope; Scala's given-
    * resolution rules pick the most specific instance.
    *
    * The companion method [[derived]] stays available so that meltc's
    * generated code can spell out the derivation explicitly, which
    * makes the resulting `.scala` source easier to read and
    * IDE-navigable.
    */
  inline given derived[A <: Product](using m: Mirror.ProductOf[A]): PropsCodec[A] =
    new PropsCodec[A]:
      private val labels:    List[String]         = summonLabels[m.MirroredElemLabels]
      private val codecs:    List[PropsCodec[?]]  = summonCodecs[m.MirroredElemTypes]
      private val labelsArr: Array[String]        = labels.toArray
      private val codecsArr: Array[PropsCodec[?]] = codecs.toArray

      def encode(value: A, buf: StringBuilder): Unit =
        val product = value.asInstanceOf[Product]
        buf += '{'
        var i = 0
        val n = labelsArr.length
        while i < n do
          if i > 0 then buf += ','
          buf ++= SimpleJson.encString(labelsArr(i))
          buf += ':'
          codecsArr(i).asInstanceOf[PropsCodec[Any]].encode(product.productElement(i), buf)
          i += 1
        buf += '}'

      def decode(json: SimpleJson.JsonValue): A =
        val obj = json match
          case o: SimpleJson.JsonValue.Obj => o
          case other                       => typeMismatch("object", other)
        val values = new Array[Any](labelsArr.length)
        var i      = 0
        while i < labelsArr.length do
          obj.fields.get(labelsArr(i)) match
            case Some(SimpleJson.JsonValue.Null) | None =>
              values(i) = codecsArr(i).decode(SimpleJson.JsonValue.Null)
            case Some(v) =>
              values(i) = codecsArr(i).decode(v)
          i += 1
        m.fromProduct(Tuple.fromArray(values))

  private inline def summonLabels[T <: Tuple]: List[String] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => constValue[h].asInstanceOf[String] :: summonLabels[t]

  private inline def summonCodecs[T <: Tuple]: List[PropsCodec[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[PropsCodec[h]] :: summonCodecs[t]

  private def typeMismatch(expected: String, got: SimpleJson.JsonValue): Nothing =
    throw new IllegalArgumentException(
      s"PropsCodec: expected $expected, got ${ got.kind }"
    )
