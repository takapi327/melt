/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.json.{ PropsCodec, SimpleJson }

/** The result of a form action (see [[ServerMeltKitPlatform.page]]).
  *
  * `A` is the shared form-state type carried by both success and failure — it
  * typically holds the submitted values plus validation errors, so the page can
  * re-render with the user's input preserved.
  *
  *   - [[Success]]  — the action succeeded; `data` is exposed to the page as its
  *                    `form` prop. Prefer [[Redirect]] for Post/Redirect/Get.
  *   - [[Failure]]  — a handled failure (e.g. validation). Returned by [[fail]].
  *                    The page re-renders with `data` and the given status; the
  *                    HTTP response stays on the same URL.
  *   - [[Redirect]] — Post/Redirect/Get. `seeOther = true` emits `303 See Other`
  *                    (the correct status after a form POST); `false` emits 302.
  */
enum ActionResult[+A]:
  case Success(data: A)
  case Failure(status: StatusCode, data: A)
  case Redirect(location: String, seeOther: Boolean = true)

object ActionResult:

  /** Serializes an [[ActionResult]] into the JSON envelope consumed by the
    * client `use:enhance` runtime:
    *
    * {{{
    * {"type":"success","status":200,"data":<A>}
    * {"type":"failure","status":422,"data":<A>}
    * {"type":"redirect","status":303,"location":"/dashboard"}
    * }}}
    *
    * `data` is encoded via the component's [[PropsCodec]] (the same codec used
    * for hydration props), so the client can decode it into the identical type.
    */
  def toJson[A](result: ActionResult[A])(using codec: PropsCodec[A]): String =
    val sb = new StringBuilder
    result match
      case Success(data) =>
        sb ++= """{"type":"success","status":200,"data":"""
        codec.encode(data, sb)
        sb += '}'
      case Failure(status, data) =>
        sb ++= s"""{"type":"failure","status":${ status: Int },"data":"""
        codec.encode(data, sb)
        sb += '}'
      case Redirect(location, _) =>
        sb ++= """{"type":"redirect","status":303,"location":"""
        sb ++= SimpleJson.encString(location)
        sb += '}'
    sb.toString

/** Returns a handled failure that preserves `data` (submitted values + errors)
  * on the same URL — the SvelteKit `fail(status, data)` equivalent.
  *
  * Use this for validation errors instead of throwing: a thrown exception is an
  * *unexpected* error (rendered by an error boundary), whereas `fail` is a
  * *normal* response whose `data` flows back into the page's `form` prop.
  *
  * {{{
  * ctx.body.form[LoginForm].map {
  *   case Right(f) if f.email.isEmpty =>
  *     fail(422, f.copy(errors = List(FieldError("email", "required"))))
  *   case Right(f)   => ActionResult.Redirect("/dashboard")
  *   case Left(err)  => fail(400, LoginForm(errors = List(FieldError("_", err.message))))
  * }
  * }}}
  */
def fail[A](status: StatusCode, data: A): ActionResult[A] = ActionResult.Failure(status, data)
