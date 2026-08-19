# echarts-chart

Uses the npm package [ECharts](https://echarts.apache.org/) via `@JSImport` and
renders a chart inside a Melt component, updating it reactively. Shows how to use
any npm/JS library from a `.melt` file.

## Highlights

- The ECharts `@JSImport` facade lives in `Chart.melt`'s
  **`<script lang="scala" module>`**. Module scripts are emitted at the top level
  of the generated `object Chart`, so `@js.native @JSImport("echarts", ...)` can
  live inside the `.melt` file.
- The chart DOM is referenced with `bind:this={chartRef}`; `onMount` calls
  `ECharts.init(el).setOption(...)`.
- An `effect` subscribes to `State[List[Int]]` and calls `setOption` to redraw
  (the "Randomize" button).
- Bundling is done by **Vite**. A small local resolver in `vite.config.mjs`
  maps `import "scalajs:main.js"` (in `index.js`) to the sbt-linked output, and
  Vite resolves the bare `"echarts"` import that Scala.js emits from
  `node_modules` (no webpack / scalajs-bundler).

## Run

```bash
# 1. Install npm deps (echarts / vite / @melt/vite-plugin)
pnpm install

# 2. Link Scala.js (dev uses fastLinkJS; ESModule output)
sbt "echarts-chart/fastLinkJS"

# 3. Start the Vite dev server from examples/echarts-chart (Node 20.19+/22.12+)
npx vite
```

A bar chart appears in the browser, and "Randomize" updates the values.
For `vite build`, run `sbt "echarts-chart/fullLinkJS"` first (the resolver
switches between dev=fastopt / build=opt automatically).

> Why not `@scala-js/vite-plugin-scalajs`: it reads the last stdout line of
> `sbt "print .../fullLinkJSOutput"` as the output path, but **sbt 2.0.0 prints
> `[success]` and an ANSI `ESC[0J` after the path**, so the last line is not the
> directory and it breaks. `vite.config.mjs` instead uses a small local resolver
> that points at the linked output directly.
