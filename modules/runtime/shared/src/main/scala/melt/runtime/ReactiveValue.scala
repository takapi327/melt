/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Common supertype for [[Var]][A] and [[Signal]][A].
  *
  * Enables N-dependency `effect` and `layoutEffect` overloads that accept a
  * variable-length sequence of reactive dependencies without losing type safety.
  *
  * This trait is intentionally not `sealed`: its subtypes ([[Var]] and
  * [[Signal]]) live in separate source files, which is incompatible with
  * Scala 3's `sealed` restriction (same compilation unit required).
  *
  * Users should not implement this trait directly; it exists solely as a
  * structural constraint for the N-dependency effect API.
  */
trait ReactiveValue[A]:
  def value: A
  private[runtime] def subscribePost(f: A => Unit): () => Unit
  private[runtime] def subscribePre(f: A => Unit): () => Unit

/** Extracts the value type `A` from a [[ReactiveValue]][A].
  * Used by the N-dependency [[effect]] / [[layoutEffect]] overloads to map a
  * tuple of reactive values `(Var[A], Signal[B], ...)` to its value types
  * `(A, B, ...)` via `Tuple.Map[T, ValueOf]`.
  */
type ValueOf[R] = R match
  case ReactiveValue[a] => a
