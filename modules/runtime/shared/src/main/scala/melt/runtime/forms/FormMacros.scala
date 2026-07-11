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
        s"nameOf expects a simple field selector such as `_.email`, but got: ${ term.show }"
      )

    // Unwrap the inlined lambda down to the `x.field` access and return `field`.
    def extract(term: Term): String =
      term match
        case Inlined(_, _, inner)               => extract(inner)
        case Block(List(defDef: DefDef), _)     => defDef.rhs.fold(bail(term))(extract) // lambda body
        case Block(Nil, inner)                  => extract(inner)
        case Typed(inner, _)                    => extract(inner)
        case Select(Ident(_), name)             => name                                 // x.field
        case Apply(Select(Ident(_), name), Nil) => name                                 // x.field (getter)
        case _                                  => bail(term)

    Expr(extract(selector.asTerm))
