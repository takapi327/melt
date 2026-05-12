/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import { createRequire } from 'node:module'
import type { Plugin } from 'vite'

export interface MeltPluginOptions {
  /**
   * Options forwarded to @scala-js/vite-plugin-scalajs.
   * When provided, the scalajs plugin is prepended to the returned plugin array.
   *
   * Requires `@scala-js/vite-plugin-scalajs` to be installed separately:
   *   pnpm add -D @scala-js/vite-plugin-scalajs
   */
  scalajs?: Record<string, unknown>
}

/**
 * Official Vite plugin for the Melt framework.
 *
 * Automatically applies:
 * 1. `preserveEntrySignatures: 'exports-only'` — preserves the `hydrate` named export
 *    so SSR hydration bootstrap works correctly after Vite/Rollup production build.
 * 2. `_melt_import_N` bare import transform — converts Scala.js `@JSImport(SideEffect)`
 *    generated `import * as _melt_import_N from "..."` to `import "..."` so that
 *    Vite processes CSS and JS as side-effect imports rather than CSS Modules.
 *
 * Optionally, when `options.scalajs` is provided, also includes
 * `@scala-js/vite-plugin-scalajs` (must be installed separately).
 *
 * @example Basic usage (SSR / no Vite dev-server HMR needed)
 * ```ts
 * // vite.config.ts
 * import { melt } from '@melt/vite-plugin'
 * export default { plugins: [melt()] }
 * ```
 *
 * @example With Scala.js Vite dev-server integration (SPA / HMR)
 * ```ts
 * // vite.config.ts
 * import { melt } from '@melt/vite-plugin'
 * export default { plugins: [melt({ scalajs: {} })] }
 * ```
 */
export function melt(options: MeltPluginOptions = {}): Plugin[] {
  const plugins: Plugin[] = [meltBuildConfig(), meltSideEffectImports()]

  if (options.scalajs !== undefined) {
    let scalajsPlugin: ((opts?: Record<string, unknown>) => Plugin) | undefined
    try {
      const req = createRequire(import.meta.url)
      scalajsPlugin = (req('@scala-js/vite-plugin-scalajs') as { default: typeof scalajsPlugin }).default
    } catch {
      throw new Error(
        '[melt:vite-plugin] @scala-js/vite-plugin-scalajs is required when using the `scalajs` option.\n' +
          'Install it with: pnpm add -D @scala-js/vite-plugin-scalajs',
      )
    }
    plugins.unshift(scalajsPlugin!(options.scalajs))
  }

  return plugins
}

/**
 * Sets `preserveEntrySignatures: 'exports-only'` via Vite's `config` hook.
 *
 * Rollup drops named exports by default when bundling entry points.
 * SSR hydration bootstrap calls `import("...").then(m => m.hydrate?.())`,
 * so the `hydrate` export must be preserved or client-side event listeners
 * silently go unregistered.
 */
function meltBuildConfig(): Plugin {
  return {
    name: 'melt:build-config',
    config() {
      return {
        build: {
          rollupOptions: {
            preserveEntrySignatures: 'exports-only',
          },
        },
      }
    },
  }
}

/**
 * Transforms Scala.js `@JSImport(SideEffect)` generated imports to true bare imports.
 *
 * Scala.js compiles:
 *   `@js.native @JSImport(path, JSImport.SideEffect) private object _melt_import_N`
 * to:
 *   `import * as _melt_import_N from "..."`
 * rather than the expected bare `import "..."`.
 *
 * Vite interprets `import * as X from "path.css"` as a CSS Modules request, which
 * causes double-inclusion and breaks global CSS. This plugin rewrites only
 * `_melt_import_`-prefixed imports to `import "..."` so Vite handles them correctly.
 */
function meltSideEffectImports(): Plugin {
  return {
    name: 'melt:side-effect-imports',
    transform(code, id) {
      if (!id.endsWith('.js')) return
      return code.replace(
        /import \* as _melt_import_\d+ from ("(?:[^"\\]|\\.)+");/g,
        (_, specifier: string) => `import ${specifier};`,
      )
    },
  }
}
