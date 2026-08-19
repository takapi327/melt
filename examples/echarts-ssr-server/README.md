# echarts-ssr-server

**Runs a page that uses an npm package (ECharts / `@JSImport`) with SSR +
hydration on a MeltKit[Future] Undertow server.** Demonstrates that it not only
builds as JS but keeps working through SSR and hydration.

- Client: the crossProject [`echarts-ssr`](../echarts-ssr) (`ChartPage.melt` in
  `shared` + the platform-split `ChartHost`: JVM = no-op, JS = `@JSImport("echarts")`).
- Server: this `MeltKit[Future]` + the built-in `UndertowServer` (JVM, no
  cats-effect / http4s).

## How it works

1. **SSR (server)**: `ChartPage` renders its static shell (chart placeholder
   `<div id="melt-echarts">`, an initial-data fallback list, and a button) as an
   HTML string. `onMount` is a no-op on the JVM, so echarts is never touched.
2. **Hydration (browser)**: the `import("/chart-page.js").then(m => m.hydrate?.())`
   injected into `%melt.head%` hydrates the SSR'd DOM, and `onMount` draws the
   chart with `ChartHost.render`. The button and `State` stay live; `effect(data)`
   redraws.
3. **Resolving echarts**: in dev the server serves the raw Scala.js ES modules, so
   there is no bundler. The **import map** in `index.html` resolves the bare
   `"echarts"` specifier from a CDN.

## Run

```bash
cd examples
sbt "echarts-ssrJS/fastLinkJS"    # link the hydration bundle
sbt "echarts-ssr-server/run"      # open http://localhost:9092
```

The SSR'd table appears first; after hydration a bar chart is drawn above it, and
"Randomize" updates both the table and the chart.

## Verified (curl)

- `GET /` → HTTP 200. The SSR HTML has the shell, the initial data
  (`<li>120</li>` …), the hydration marker (`[melt:chart-page`), a props payload,
  the import map, and the hydrate script.
- `GET /chart-page.js` and the split modules → HTTP 200. `components.-Chart-Host$.js`
  contains `import * as … from "echarts"`, which the import map resolves.

> For a prod build (hashed assets + bundled echarts), build `echarts-ssr` with
> Vite (`melt()` + `meltViteInputGenerate` + an echarts `resolve.alias`) to
> produce `dist/` and `.vite/manifest.json`, then point the server's
> `meltkitViteDistDir` / `meltkitViteManifestPath` at them (same flow as http4s-ssr).
