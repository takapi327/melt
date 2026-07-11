/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.forms

import melt.runtime.json.{ PropsCodec, SimpleJson }
import melt.runtime.State

/** Non-generic view of a [[Form]] used by the client `use:enhance` action.
  *
  * The action lives in the meltkit browser layer (it needs the router + fetch),
  * so it operates on this erased interface rather than on `Form[A]` directly —
  * the concrete decode is delegated back to the codec captured by [[Form]].
  */
trait FormHandle:

  /** `true` while an enhanced submit is in flight (for disabling buttons etc.). */
  def submitting: State[Boolean]

  /** Decodes the `data` field of an action-result envelope with the form's own
    * [[PropsCodec]] and updates the reactive form state.
    */
  def applyResultData(json: SimpleJson.JsonValue): Unit

  /** Invoked by the `enhance` action just before submitting. Return `false` to
    * cancel the submit (e.g. client-side validation failed). Default: `true`.
    */
  def beforeSubmit(): Boolean

  /** Invoked by the `enhance` action once the server responds. `kind` is
    * `"success" | "failure" | "redirect"`; `applyDefault` runs the built-in
    * behaviour (update the form / reset / navigate). If no [[Form.onResult]]
    * handler is registered, `applyDefault` is called automatically. A handler
    * that never calls `applyDefault` takes full manual control.
    */
  def afterResult(kind: String, applyDefault: () => Unit): Unit

/** Reactive form state, seeded from the page's `form` prop.
  *
  * ''Read reactively via `form.data.value`'' — the Melt compiler classifies an
  * interpolation as reactive only when it textually contains `.value` (or
  * references a `State`/`Signal` variable), so `form.data.value.email` re-renders
  * on update while a helper like `form.error("x")` would be treated as static.
  *
  * {{{
  * <script lang="scala">
  * case class Props(form: Option[LoginForm] = None)
  * val form = Form(props.form.getOrElse(LoginForm()))
  * </script>
  *
  * <form method="post" use:enhance={form}>
  *   <input name="email" value={form.data.value.email}/>
  *   {form.data.value.errors.find(_.field == "email").map(e => <span>{e.message}</span>)}
  * </form>
  * }}}
  *
  * On the JVM (SSR) `State` is a no-op holder: `data.value` returns the seed for
  * the initial render, and `set` does nothing (rendering is one-shot). On the
  * client the same `Form` is rebuilt from the hydrated seed and `enhance`
  * updates it in place.
  */
final class Form[A](initial: A)(using codec: PropsCodec[A]) extends FormHandle:

  /** The reactive form value. Read with `form.data.value`. */
  val data: State[A] = State(initial)

  val submitting: State[Boolean] = State(false)

  def applyResultData(json: SimpleJson.JsonValue): Unit =
    data.set(codec.decode(json))

  private var _before: () => Boolean                        = () => true
  private var _after:  Option[(String, () => Unit) => Unit] = None

  /** Registers a pre-submit hook. Return `false` to cancel (fluent). */
  def onSubmit(f: () => Boolean): this.type =
    _before = f
    this

  /** Registers a result hook that receives the result `kind` and an
    * `applyDefault` thunk (fluent). Call `applyDefault()` to keep the built-in
    * behaviour, or omit it to handle the result manually.
    */
  def onResult(f: (String, () => Unit) => Unit): this.type =
    _after = Some(f)
    this

  def beforeSubmit(): Boolean = _before()

  def afterResult(kind: String, applyDefault: () => Unit): Unit =
    _after match
      case Some(f) => f(kind, applyDefault)
      case None    => applyDefault()

object Form:

  def apply[A](initial: A)(using PropsCodec[A]): Form[A] = new Form(initial)
