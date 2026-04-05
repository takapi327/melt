/** Copyright (c) 2026 by Takahiko Tominaga This software is licensed under the Apache License,
  * Version 2.0 (the "License"). For more information see LICENSE or
  * https://www.apache.org/licenses/LICENSE-2.0
  */

package meltc.sbt

import sbt._
import sbt.Keys._

/** sbt-meltc plugin
  *
  * Detects .melt files and invokes the meltc compiler to generate .scala files.
  *
  * Phase 0: stub implementation Integration with MeltCompiler will be implemented in Phase 3.
  */
object MeltcPlugin extends AutoPlugin {

  // Auto-triggering will be considered in Phase 3.
  // For now, enable explicitly with enablePlugins(MeltcPlugin).
  override def trigger  = noTrigger
  override def requires = plugins.JvmPlugin

  object autoImport {
    val meltcSourceDirectory =
      settingKey[File]("Directory containing .melt source files (default: src/main/components)")
    val meltcOutputDirectory =
      settingKey[File]("Directory for generated .scala files (default: target/.../meltc)")
    val meltcGenerate =
      taskKey[Seq[File]]("Compile .melt files to .scala files")
  }

  import autoImport._

  override def projectSettings: Seq[Setting[_]] = Seq(
    meltcSourceDirectory := (Compile / sourceDirectory).value / "components",
    meltcOutputDirectory := (Compile / sourceManaged).value / "meltc",
    meltcGenerate        := Seq.empty,
    Compile / sourceGenerators += meltcGenerate.taskValue
  )
}
