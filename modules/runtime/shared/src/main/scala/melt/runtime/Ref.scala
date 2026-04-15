/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A mutable reference to a DOM element, used with `bind:this`.
  *
  * {{{
  * val canvasRef = Ref.empty[Element]
  * // later, after mount:
  * canvasRef.foreach(el => ...)
  * }}}
  */
final class Ref[A]:
  private var _el: Option[A] = None

  def get: Option[A] = _el

  def set(el: A): Unit = _el = Some(el)

  def foreach(f: A => Unit): Unit = _el.foreach(f)

object Ref:
  def empty[A]: Ref[A] = new Ref[A]
