/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.quoted.*
import scala.NamedTuple.AnyNamedTuple
import scala.NamedTuple.Concat
import scala.NamedTuple.NamedTuple as NT

import meltkit.codec.PathParamDecoder
import meltkit.codec.PathParamEncoder

/** A typed route that carries its **full path in the type** — static segments as singleton
  * `String` literal types and dynamic segments as [[PathParam]]`[N, A]`, in order — via
  * `Segs`, alongside the params NamedTuple `P` (used by handlers).
  *
  * A [[TypedRoute]] converts to a [[PathSpec]]`[P]` for runtime routing (`app.get(route)`), while
  * its `Segs` type drives **compile-time link checking** through the `route"..."`
  * interpolator (see [[RouteRegistry]]). This is the productionised form of the type-safe-links
  * PoC: one definition yields both the compile-time type and the runtime segments/codecs.
  *
  * {{{
  * val user = TypedRoute.root / "users" / param[Int]("id")   // TypedRoute[(id: Int), ("users", PathParam["id", Int])]
  * app.get(user) { ctx => ... }                          // converts to PathSpec[(id: Int)]
  * }}}
  */
final class TypedRoute[P <: AnyNamedTuple, Segs <: Tuple] private[meltkit] (
  private[meltkit] val segments:      List[PathSegment],
  private[meltkit] val paramDecoders: List[(String, PathParamDecoder[?])],
  private[meltkit] val paramEncoders: List[(String, PathParamEncoder[?])]
):

  /** Appends a static segment; its singleton literal type is added to `Segs`. */
  def /(s: String & Singleton): TypedRoute[P, Tuple.Append[Segs, s.type]] =
    new TypedRoute(segments ++ PathSpec.staticSegments(s), paramDecoders, paramEncoders)

  /** Appends a dynamic segment; `PathParam[N, A]` is added to `Segs` and `(N: A)` to `P`. */
  def /[N <: String, A](
    p: PathParam[N, A]
  ): TypedRoute[Concat[P, NT[N *: EmptyTuple, A *: EmptyTuple]], Tuple.Append[Segs, PathParam[N, A]]] =
    new TypedRoute(
      segments :+ PathSegment.Param(p.paramName),
      paramDecoders :+ (p.paramName -> p.decoder),
      paramEncoders :+ (p.paramName -> p.encoder)
    )

  /** The runtime [[PathSpec]] for routing (drops the `Segs` type; routing uses `segments`). */
  def toPathSpec: PathSpec[P] = PathSpec.of[P](segments, paramDecoders, paramEncoders)

object TypedRoute:

  /** The empty root route: `TypedRoute.root / "users" / param[Int]("id")`. */
  val root: TypedRoute[PathSpec.Empty, EmptyTuple] = new TypedRoute(Nil, Nil, Nil)

  /** Lets a [[TypedRoute]] be passed wherever a [[PathSpec]] is expected (`app.get(route)`). */
  given routeToPathSpec[P <: AnyNamedTuple, S <: Tuple]: Conversion[TypedRoute[P, S], PathSpec[P]] =
    _.toPathSpec

/** A compile-time registry pointing at the object that holds the route `val`s. The
  * `route"..."` interpolator reflects on `R`'s members to collect every [[TypedRoute]]
  * automatically — no need to list route `.type`s by hand.
  *
  * {{{
  * object Routes:
  *   val user = TypedRoute.root / "users" / param[Int]("id")
  *   val post = TypedRoute.root / "posts" / param[String]("slug")
  *
  * // in a *separate* scope (so param's implicit search does not evaluate this given):
  * given RouteRegistry[Routes.type] = RouteRegistry()
  * }}}
  */
final class RouteRegistry[R]
object RouteRegistry:
  def apply[R](): RouteRegistry[R] = new RouteRegistry[R]

/** `route"/users/$id"` — a compile-time-checked link. Validates the literal path structure and
  * each interpolated parameter's type against every [[TypedRoute]] found in the in-scope
  * [[RouteRegistry]]'s object; an unknown route, wrong parameter type, or wrong arity is a
  * **compile error**. Parameters are serialised with their [[PathParamEncoder]].
  */
extension (inline sc: StringContext)
  inline def route(inline args: Any*)(using inline reg: RouteRegistry[?]): String =
    ${ RouteMacros.routeImpl('sc, 'args, 'reg) }

private[meltkit] object RouteMacros:

  def routeImpl(scE: Expr[StringContext], argsE: Expr[Seq[Any]], regE: Expr[RouteRegistry[?]])(using
    Quotes
  ): Expr[String] =
    import quotes.reflect.*

    def elems(t: TypeRepr): List[TypeRepr] =
      t.asType match
        case '[EmptyTuple] => Nil
        case '[h *: tl]    => TypeRepr.of[h] :: elems(TypeRepr.of[tl])
        case _             => List(t)

    // Decode a route's `Segs` tuple into an ordered list of literal segments / typed params.
    def decodeSegs(segs: TypeRepr): List[Either[String, TypeRepr]] =
      elems(segs).flatMap {
        // static literal type: may itself contain '/', mirror runtime staticSegments split
        case ConstantType(StringConstant(lit)) => lit.split('/').filter(_.nonEmpty).toList.map(Left(_))
        case s                                 =>
          s.asType match
            case '[PathParam[n, a]] => List(Right(TypeRepr.of[a]))
            case _                  => report.errorAndAbort(s"unexpected route segment: ${ s.show }")
      }

    // Reflect on the registry object `R` and collect every `TypedRoute[P, Segs]` member.
    val objTpe = regE.asTerm.tpe.widen match
      case AppliedType(_, List(r)) => r
      case other                   => report.errorAndAbort(s"cannot read RouteRegistry type: ${ other.show }")
    val typedRouteClass = Symbol.requiredClass("meltkit.TypedRoute")
    val routes: List[List[Either[String, TypeRepr]]] =
      objTpe.typeSymbol.fieldMembers.flatMap { f =>
        objTpe.memberType(f).widen match
          case AppliedType(tc, List(_, segs)) if tc.typeSymbol == typedRouteClass => Some(decodeSegs(segs))
          case _                                                                  => None
      }
    if routes.isEmpty then report.errorAndAbort(s"RouteRegistry object ${ objTpe.show } has no TypedRoute members")

    val parts = scE match
      case '{ StringContext(${ Varargs(ps) }*) } => ps.toList.map(_.valueOrAbort)
      case _                                     => report.errorAndAbort("route: literal parts required")
    val args = argsE match
      case Varargs(as) => as.toList
      case _           => report.errorAndAbort("route: explicit args required")

    val sb = new StringBuilder(parts.head)
    for i <- args.indices do sb.append(' ').append(i).append(' ').append(parts(i + 1))
    val segs = sb.toString.split('/').toList.filter(_.nonEmpty)
    def argIdx(tok: String): Option[Int] =
      if tok.startsWith(" ") && tok.endsWith(" ") then tok.drop(1).dropRight(1).toIntOption else None

    def matches(route: List[Either[String, TypeRepr]]): Boolean =
      route.length == segs.length && route.zip(segs).forall {
        case (Left(lit), tok)  => argIdx(tok).isEmpty && lit == tok
        case (Right(tpe), tok) => argIdx(tok).exists(i => args(i).asTerm.tpe.widen <:< tpe)
      }

    val pretty =
      segs.map(t => argIdx(t).map(i => "${" + args(i).asTerm.tpe.widen.show + "}").getOrElse(t)).mkString("/", "/", "")
    if !routes.exists(matches) then
      report.errorAndAbort(s"no route matches '$pretty' (unknown path or wrong parameter type)")

    // Build the runtime URL: static parts verbatim, each parameter via its PathParamEncoder
    // (so custom types serialize correctly — not a bare toString). Segment percent-encoding
    // is a follow-up (needs a cross-platform URI-component encoder; see design memo §11).
    val encoded: List[Expr[String]] = args.map { arg =>
      arg.asTerm.tpe.widen.asType match
        case '[t] =>
          Expr.summon[PathParamEncoder[t]] match
            case Some(enc) => '{ ${ enc }.encode(${ arg.asExprOf[t] }) }
            case None      =>
              report.errorAndAbort(s"no PathParamEncoder in scope for ${ arg.asTerm.tpe.widen.show }", arg)
    }
    val pieces: List[Expr[String]] =
      Expr(parts.head) :: encoded.zip(parts.tail).flatMap { case (e, p) => List(e, Expr(p)) }
    pieces.reduceLeft((a, b) => '{ ${ a } + ${ b } })
