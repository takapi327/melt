/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import scala.quoted.*

/** Compile-time support for [[Form.nameOf]].
  *
  * Extracts a field name from a simple selector lambda (`_.email`) so that the
  * HTML `name` attribute of a form input can be derived from a type-checked
  * accessor rather than a hand-written string — a typo (`_.emial`) becomes a
  * compile error, and the derived name is exactly the case-class field label
  * that `FormDataDecoder` reads.
  */
private[forms] object FormMacros:

  def nameOfImpl[T: Type](selector: Expr[T])(using Quotes): Expr[String] =
    import quotes.reflect.*

    def bail(term: Term): Nothing =
      report.errorAndAbort(
        s"nameOf expects a field selector such as `_.email` or a nested `_.address.city`, but got: ${ term.show }"
      )

    // Only case-class fields form the path — a method call like `_.email.trim`
    // is rejected so the derived name always matches a real (decodable) field.
    def isField(sel: Select): Boolean = sel.symbol.flags.is(Flags.CaseAccessor)

    // Unwrap the inlined lambda and collect the chain of field accesses, so
    // `_.email` -> ["email"] and `_.address.city` -> ["address", "city"].
    def segments(term: Term): List[String] =
      term match
        case Inlined(_, _, inner)                     => segments(inner)
        case Block(List(defDef: DefDef), _)           => defDef.rhs.fold(bail(term))(segments) // lambda body
        case Block(Nil, inner)                        => segments(inner)
        case Typed(inner, _)                          => segments(inner)
        case Ident(_)                                 => Nil                                   // the lambda parameter
        case sel @ Select(qual, name) if isField(sel) => segments(qual) :+ name                // x.field / x.a.b
        case Apply(sel @ Select(qual, name), Nil) if isField(sel) => segments(qual) :+ name // getter form
        case _                                                    => bail(term)

    val path = segments(selector.asTerm)
    if path.isEmpty then bail(selector.asTerm)
    Expr(path.mkString("."))
