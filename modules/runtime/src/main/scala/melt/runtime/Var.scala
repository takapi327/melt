/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** リアクティブな可変値。
  *
  * Phase 0: スタブ実装
  * Phase 1 で complete な実装を行う予定。
  */
class Var[A] private (private var current: A)

object Var:
  def apply[A](initial: A): Var[A] = new Var(initial)
