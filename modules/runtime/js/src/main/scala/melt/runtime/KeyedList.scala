/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A list paired with a key function for efficient diffing in [[Bind.each]].
  *
  * Created via the `keyed` extension method on `Var[List[A]]` or `Signal[Seq[A]]`.
  *
  * {{{
  * val items = Var(List(Todo(1, "a"), Todo(2, "b")))
  * // In template: {items.keyed(_.id).map(item => <li>...</li>)}
  * }}}
  */
case class KeyedList[A, K](source: Var[List[A]], keyFn: A => K)

extension [A](v: Var[List[A]])
  /** Creates a [[KeyedList]] for keyed list rendering with `Bind.each`. */
  def keyed[K](f: A => K): KeyedList[A, K] = KeyedList(v, f)
