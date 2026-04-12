/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import melt.runtime.impl.VarFactory

/** A mutable reactive variable — the common API contract used by both the
  * Scala.js (client) and JVM (SSR) runtimes.
  *
  * Each platform provides its own implementation under
  * [[melt.runtime.impl.VarFactory]]:
  *
  *   - '''JS (Scala.js)''' — a real reactive cell. `set` / `update` notify
  *     subscribers in three ordered phases (Pre → Bind → Post), propagate
  *     through derived [[Signal]] instances, and integrate with the
  *     `Owner` / `Batch` / `Cleanup` machinery.
  *   - '''JVM (SSR)''' — a no-op wrapper that merely holds the initial value.
  *     `set` / `update` do nothing and no subscribers are ever invoked. This
  *     matches SSR semantics: the template only renders an initial snapshot,
  *     and event handlers are stripped from the generated code.
  *
  * User and generated code reference `Var` through the shared factory, which
  * dispatches to the correct implementation automatically:
  *
  * {{{
  * val count   = Var(0)         // JVM → JvmVar; JS → JsVar
  * val doubled = count.map(_ * 2)
  * count += 1                   // JS: doubled.now() == 2; JVM: still 0 (no-op)
  * }}}
  *
  * == Design note: trait + companion placement ==
  *
  * Scala 3 requires a `trait` and its companion `object` to live in the same
  * source file (compiler error `E161`). This file therefore declares both
  * `trait Var[A]` and `object Var`, and the object delegates instance
  * construction to a platform-provided
  * [[melt.runtime.impl.VarFactory]] that lives under each platform's own
  * source tree. See `docs/meltc-ssr-design.md` §5.1 for full rationale.
  */
trait Var[A]:

  /** Returns the current value without registering a reactive dependency. */
  def now(): A

  /** Replaces the current value and notifies all subscribers (JS only;
    * no-op on JVM).
    */
  def set(value: A): Unit

  /** Updates the current value via `f` and notifies subscribers
    * (JS only; no-op on JVM).
    */
  def update(f: A => A): Unit

  /** Subscribes to future value changes in the Bind phase. Returns an
    * unsubscribe function.
    *
    * On JVM this never fires, and the returned function is a no-op.
    */
  def subscribe(f: A => Unit): () => Unit

  /** Derives a new [[Signal]] by transforming each emitted value with `f`.
    *
    * On JVM this computes `f` exactly once against the initial value and
    * returns a frozen signal.
    */
  def map[B](f: A => B): Signal[B]

  /** Derives a new [[Signal]] by flat-mapping, supporting dynamic source
    * switching. On JVM this computes once against the initial value.
    */
  def flatMap[B](f: A => Signal[B]): Signal[B]

  /** Returns a read-only view of this variable as a [[Signal]]. */
  def signal: Signal[A]

  // ── Runtime-internal hooks ─────────────────────────────────────────────────
  // These exist so that the JS-side `effect` / `layoutEffect` helpers can
  // subscribe to the Pre and Post phases directly. On JVM both are no-ops.

  /** Subscribes in the Pre phase — fires before Bind DOM updates. */
  private[runtime] def subscribePre(f: A => Unit): () => Unit

  /** Subscribes in the Post phase — fires after Bind DOM updates. */
  private[runtime] def subscribePost(f: A => Unit): () => Unit

/** Shared factory. Delegates to the platform-provided
  * [[melt.runtime.impl.VarFactory]] so that `Var(0)` constructs a JVM no-op
  * or a Scala.js reactive cell automatically.
  */
object Var:
  def apply[A](initial: A): Var[A] = VarFactory.create(initial)
