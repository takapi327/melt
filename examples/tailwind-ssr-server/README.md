# tailwind-ssr-server

**Serves a Tailwind CSS page with SSR + hydration on a MeltKit[Future] Undertow
server.** The server SSRs the Tailwind-styled HTML and the browser hydrates it, so
the counter becomes interactive.

- Component: `App.melt` from the crossProject [`tailwind-ssr`](../tailwind-ssr).
  It uses `State` + Tailwind utilities only (no `@JSImport`), so it is
  **JVM-safe = full SSR + hydration** (no client-only island like echarts needs).
- Tailwind CSS: the **Tailwind CLI scans the `.melt` sources** and generates the
  CSS, which the server inlines into the template `<head>` at startup (no bundler
  on the server side).
- Hydration JS: the raw Scala.js ES modules (fastLinkJS output) are served as-is.

## Run

```bash
cd examples
sbt "tailwind-ssrJS/fastLinkJS"                       # hydration bundle

cd tailwind-ssr-server
pnpm install                                          # @tailwindcss/cli
pnpm exec tailwindcss -i tailwind.css \
  -o src/main/resources/generated.css                 # generate the CSS (scans .melt)

cd ..
sbt "tailwind-ssr-server/run"                          # http://localhost:9093
```

When you add or remove classes, re-run the Tailwind CLI to rebuild
`generated.css` (`-w` for watch mode).

## Verified (curl)

- `GET /` → HTTP 200. The SSR HTML markup has the Tailwind classes
  (`min-h-screen … bg-slate-100 …`), the `<head>` has an inline `<style>` with the
  Tailwind rules (`.bg-slate-100` / `--tw-*` variables), and the hydrate script
  `import("/app.js").then(m => m.hydrate?.())`.
- `/app.js` (the hydration module) → HTTP 200.

> Melt adds a scope class (`melt-xxxx`) to elements even without a `<style>` block,
> but it is a separate class from the Tailwind utilities, so there is no conflict.
