/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import { describe, test, expect } from 'vitest'
import type { Plugin } from 'vite'
import { melt } from './index.js'

// Helper: invoke a plugin hook by name
function invokeConfig(plugin: Plugin): object {
  const hook = plugin.config
  if (typeof hook === 'function') return (hook as Function)({}, { command: 'build', mode: 'production' }) ?? {}
  if (hook && typeof hook === 'object' && 'handler' in hook)
    return (hook.handler as Function)({}, { command: 'build', mode: 'production' }) ?? {}
  return {}
}

function invokeTransform(plugin: Plugin, code: string, id: string): string | undefined {
  const hook = plugin.transform
  if (typeof hook === 'function') return (hook as Function).call({}, code, id) ?? undefined
  if (hook && typeof hook === 'object' && 'handler' in hook)
    return (hook.handler as Function).call({}, code, id) ?? undefined
  return undefined
}

describe('melt()', () => {
  test('returns two plugins', () => {
    const plugins = melt()
    expect(plugins).toHaveLength(2)
  })

  test('plugin names are melt:build-config and melt:side-effect-imports', () => {
    const [buildConfig, sideEffect] = melt()
    expect(buildConfig.name).toBe('melt:build-config')
    expect(sideEffect.name).toBe('melt:side-effect-imports')
  })

  test('throws when scalajs option is provided but package is not installed', () => {
    expect(() => melt({ scalajs: {} })).toThrow('@scala-js/vite-plugin-scalajs')
  })
})

describe('melt:build-config', () => {
  test('config hook sets preserveEntrySignatures to exports-only', () => {
    const [buildConfig] = melt()
    const config = invokeConfig(buildConfig) as { build?: { rollupOptions?: { preserveEntrySignatures?: string } } }
    expect(config.build?.rollupOptions?.preserveEntrySignatures).toBe('exports-only')
  })
})

describe('melt:side-effect-imports', () => {
  const [, sideEffect] = melt()

  test('transforms _melt_import_N CSS import to bare import', () => {
    const code = `import * as _melt_import_0 from "/styles/global.css";`
    const result = invokeTransform(sideEffect, code, 'main.js')
    expect(result).toBe(`import "/styles/global.css";`)
  })

  test('transforms _melt_import_N JS import to bare import', () => {
    const code = `import * as _melt_import_0 from "/scripts/analytics.js";`
    const result = invokeTransform(sideEffect, code, 'main.js')
    expect(result).toBe(`import "/scripts/analytics.js";`)
  })

  test('transforms multiple _melt_import_N imports', () => {
    const code = [
      `import * as _melt_import_0 from "/styles/global.css";`,
      `import * as _melt_import_1 from "/scripts/analytics.js";`,
    ].join('\n')
    const result = invokeTransform(sideEffect, code, 'main.js')
    expect(result).toBe([
      `import "/styles/global.css";`,
      `import "/scripts/analytics.js";`,
    ].join('\n'))
  })

  test('does not transform imports without _melt_import_ prefix', () => {
    const code = `import * as foo from "/styles/other.css";`
    const result = invokeTransform(sideEffect, code, 'main.js')
    // no _melt_import_ prefix — code is returned unchanged
    expect(result).toBe(code)
  })

  test('does not transform non-.js files', () => {
    const code = `import * as _melt_import_0 from "/styles/global.css";`
    const result = invokeTransform(sideEffect, code, 'main.ts')
    expect(result).toBeUndefined()
  })

  test('handles escaped characters in specifier', () => {
    const code = `import * as _melt_import_0 from "/styles/path\\/with-slash.css";`
    const result = invokeTransform(sideEffect, code, 'main.js')
    expect(result).toBe(`import "/styles/path\\/with-slash.css";`)
  })
})
