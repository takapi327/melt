/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** JVM no-op implementation of Document.
  *
  * SSR has no live document — title and event listeners are silently ignored.
  */
object Document:
  def title(t:  Signal[String]):                                  Unit = ()
  def title(t:  Var[String]):                                     Unit = ()
  def title(t:  String):                                          Unit = ()
  def on(event: String)(handler: melt.runtime.dom.Event => Unit): Unit = ()
  def bindVisibilityState(v:    Var[String]):                                   Unit = ()
  def bindFullscreenElement(v:  Var[Option[melt.runtime.dom.Element]]):         Unit = ()
  def bindPointerLockElement(v: Var[Option[melt.runtime.dom.Element]]):        Unit = ()
  def bindActiveElement(v:      Var[Option[melt.runtime.dom.Element]]):         Unit = ()
