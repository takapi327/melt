/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.Signal
import melt.runtime.Var

/** JVM no-op implementation of [[Router]].
  *
  * SSR has no browser history — navigation calls are silently ignored
  * and [[currentPath]] always yields `/`.
  */
object Router:

  private val _path: Var[String] = Var("/")

  val currentPath: Signal[String] = _path.signal

  def navigate(path: String): Unit = ()

  def replace(path: String): Unit = ()
