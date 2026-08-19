import { melt } from '@melt/vite-plugin'
import { existsSync, readdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const require   = createRequire(import.meta.url)

// The Scala.js output lives under examples/target/ (outside this project's root),
// so bare `import "echarts"` from it can't reach echarts in this project's
// node_modules. Alias the exact `echarts` specifier to its installed package dir.
const echartsDir = dirname(require.resolve('echarts/package.json'))

// Resolves `scalajs:<file>` to the sbt-linked Scala.js output.
//   dev   (`vite`)        → examples/target/.../echarts-chart-fastopt  (`fastLinkJS`)
//   build (`vite build`)  → examples/target/.../echarts-chart-opt      (`fullLinkJS`)
// We resolve the path ourselves rather than use `@scala-js/vite-plugin-scalajs`,
// whose stdout parsing of `sbt "print .../fullLinkJSOutput"` breaks on sbt 2.0.0
// (it emits `[success]` and an ANSI `ESC[0J` after the path, so the last line is
// not the directory). Run the matching `sbt …LinkJS` before starting Vite.
function scalajsOutput(command) {
  const variant = command === 'build' ? 'echarts-chart-opt' : 'echarts-chart-fastopt'
  const link    = command === 'build' ? 'fullLinkJS' : 'fastLinkJS'
  return {
    name: 'scalajs:local-output',
    enforce: 'pre',
    resolveId(source) {
      if (!source.startsWith('scalajs:')) return null
      const file = source.slice('scalajs:'.length)
      const base = resolve(__dirname, '../target/out/sjs1')
      const scalaDir = existsSync(base)
        ? readdirSync(base).find(d => d.startsWith('scala-'))
        : undefined
      const out = scalaDir && resolve(base, scalaDir, 'echarts-chart', variant, file)
      if (!out || !existsSync(out))
        throw new Error(`[scalajs] ${source} not found — run \`sbt echarts-chart/${link}\` first.`)
      return out
    },
  }
}

export default ({ command }) => ({
  plugins: [scalajsOutput(command), ...melt()],
  resolve: {
    alias: [{ find: /^echarts$/, replacement: echartsDir }],
  },
})
