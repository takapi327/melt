/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc

/** meltc コンパイラのエントリーポイント
  *
  * Phase 0: スタブ実装
  * Phase 2 以降でパーサー・コード生成を実装予定
  */
object MeltCompiler:

  /** .melt ソースを .scala コードにコンパイルする */
  def compile(source: String, filename: String): CompileResult =
    CompileResult(None, None, Nil, Nil)

/** コンパイル結果 */
case class CompileResult(
  scalaCode: Option[String],
  scopedCss:  Option[String],
  errors:     List[CompileError],
  warnings:   List[CompileWarning],
):
  def isSuccess: Boolean = errors.isEmpty

/** コンパイルエラー */
case class CompileError(
  message:  String,
  line:     Int,
  column:   Int,
  filename: String,
)

/** コンパイル警告 */
case class CompileWarning(
  message:  String,
  line:     Int,
  column:   Int,
  filename: String,
)
