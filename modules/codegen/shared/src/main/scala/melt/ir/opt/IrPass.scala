/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.ir.opt

import melt.ir.IrComponent

/** A single IR transformation pass.
  *
  * Passes are composable via [[IrOptimizer.run]].
  * Each pass receives an [[IrComponent]] and returns a (possibly modified) one.
  */
trait IrPass:
  def name:                 String
  def run(ir: IrComponent): IrComponent

object IrOptimizer:
  def run(ir: IrComponent, passes: List[IrPass] = defaultPasses): IrComponent =
    passes.foldLeft(ir)((acc, pass) => pass.run(acc))

  val defaultPasses: List[IrPass] = List(StaticHoistPass, DependencyGraphPass)
