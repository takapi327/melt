/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.sbt

import sbt._
import sbt.Keys._

/** sbt-meltc プラグイン
  *
  * .melt ファイルを検出し、meltc コンパイラを呼び出して .scala ファイルを生成する。
  *
  * Phase 0: スタブ実装
  * Phase 3 で MeltCompiler との連携を実装予定
  */
object MeltcPlugin extends AutoPlugin {

  // Phase 3 で自動有効化を検討
  // 現時点では enablePlugins(MeltcPlugin) で明示的に有効化する
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
    Compile / sourceGenerators += meltcGenerate.taskValue,
  )
}
