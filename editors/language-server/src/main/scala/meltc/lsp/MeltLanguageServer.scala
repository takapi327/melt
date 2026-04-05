/** Copyright (c) 2026 by Takahiko Tominaga This software is licensed under the Apache License,
  * Version 2.0 (the "License"). For more information see LICENSE or
  * https://www.apache.org/licenses/LICENSE-2.0
  */

package meltc.lsp

/** Melt Language Server (LSP)
  *
  * Routes each section of a .melt file to the appropriate Language Server.
  *   - `<script lang="scala">` → generates a virtual .scala file and delegates to Metals
  *   - Template `{}` expressions → delegates to Metals
  *   - `<style>` → delegates to the CSS Language Server
  *
  * Phase 0: stub implementation Full implementation using LSP4J is planned for Phase 11.
  */
class MeltLanguageServer
