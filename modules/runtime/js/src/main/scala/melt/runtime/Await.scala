/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom
import scala.concurrent.Future
import scala.util.Try

/** Renders the result of a `Future` into a Melt template.
  *
  * Returns a placeholder `<span>` element immediately. When the Future settles
  * the placeholder is replaced by the element returned by `handler`.
  *
  * When called inside a `<melt:boundary>`, the Future is also registered with
  * [[BoundaryScope]] so that the boundary can show its `<melt:pending>` UI
  * while at least one `Await` is unresolved.
  *
  * Both `Success` and `Failure` cases must be handled in the pattern match.
  * Omitting the `Failure` case will result in a runtime `MatchError` when the
  * Future fails — this is intentional Scala behaviour (Case X design decision).
  *
  * {{{
  * {Await(fetchUser()) {
  *   case Success(user)  => <p>Hello, {user.name}!</p>
  *   case Failure(error) => <p>Error: {error.getMessage()}</p>
  * }}
  * }}}
  */
object Await:
  def apply[T](future: Future[T])(handler: Try[T] => dom.Element): dom.Element =
    val placeholder = dom.document.createElement("span")
    BoundaryScope.register(future)
    future.onComplete { result =>
      placeholder.replaceWith(handler(result))
    }(scala.concurrent.ExecutionContext.Implicits.global)
    placeholder
