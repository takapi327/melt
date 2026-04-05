/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.lsp

/** Melt Language Server（LSP）
  *
  * .melt ファイルの各セクションを適切な Language Server に振り分ける。
  * - `<script lang="scala">` → 仮想 .scala を生成して Metals に委譲
  * - テンプレート `{}` 内 → Metals に委譲
  * - `<style>` → CSS LS に委譲
  *
  * Phase 0: スタブ実装
  * Phase 11 で LSP4J を使った実装を行う予定
  */
class MeltLanguageServer
