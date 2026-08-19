import { melt } from '@melt/vite-plugin'
import tailwindcss from '@tailwindcss/vite'
import { existsSync, readdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

// Resolves `scalajs:<file>` to the sbt-linked Scala.js output (dev=fastopt /
// build=opt). Same approach as examples/echarts-chart — we resolve the path
// ourselves rather than use @scala-js/vite-plugin-scalajs, whose stdout parsing
// breaks on sbt 2.0.0. Run the matching `sbt tailwind-app/…LinkJS` before Vite.
function scalajsOutput(command) {
  const variant = command === 'build' ? 'tailwind-app-opt' : 'tailwind-app-fastopt'
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
      const out = scalaDir && resolve(base, scalaDir, 'tailwind-app', variant, file)
      if (!out || !existsSync(out))
        throw new Error(`[scalajs] ${source} not found — run \`sbt tailwind-app/${link}\` first.`)
      return out
    },
  }
}

export default ({ command }) => ({
  plugins: [tailwindcss(), scalajsOutput(command), ...melt()],
})
