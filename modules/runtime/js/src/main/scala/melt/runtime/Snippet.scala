/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** A snippet — a typed template fragment that receives a value of type [[A]]
  * and produces a DOM element. Corresponds to Svelte 5's `Snippet<[A]>`.
  *
  * Usage in Props:
  * {{{
  * case class Props(
  *   renderItem: Snippet[Todo],   // Todo => dom.Node
  *   children:   () => dom.Node
  * )
  * }}}
  */
type Snippet[A] = A => dom.Node

/** A snippet taking two arguments — e.g. `{#snippet row(name: String, i: Int)}`.
  * A named alias for the raw function type used in a Props field. */
type Snippet2[A, B] = (A, B) => dom.Node

/** A snippet taking three arguments. */
type Snippet3[A, B, C] = (A, B, C) => dom.Node
