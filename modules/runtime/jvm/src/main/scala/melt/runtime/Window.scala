/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** JVM no-op implementation of Window.
  *
  * SSR has no live window — event listeners are silently ignored and
  * reactive properties return frozen default values.
  */
object Window:
  def on(event: String)(handler: melt.runtime.dom.Event => Unit): Unit = ()

  lazy val scrollY:     Signal[Double]  = Signal.pure(0.0)
  lazy val scrollX:     Signal[Double]  = Signal.pure(0.0)
  lazy val innerWidth:  Signal[Double]  = Signal.pure(0.0)
  lazy val innerHeight: Signal[Double]  = Signal.pure(0.0)
  lazy val online:      Signal[Boolean] = Signal.pure(true)

  def bindScrollY(v:          Var[Double]):  Unit = ()
  def bindScrollX(v:          Var[Double]):  Unit = ()
  def bindInnerWidth(v:       Var[Double]):  Unit = ()
  def bindInnerHeight(v:      Var[Double]):  Unit = ()
  def bindOuterWidth(v:       Var[Double]):  Unit = ()
  def bindOuterHeight(v:      Var[Double]):  Unit = ()
  def bindDevicePixelRatio(v: Var[Double]):  Unit = ()
  def bindOnline(v:           Var[Boolean]): Unit = ()
