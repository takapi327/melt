# tailwind-app

A minimal SPA that uses **Tailwind CSS v4** with Melt. Write Tailwind utility
classes in the `.melt` template's `class="..."`, and the Vite `@tailwindcss/vite`
plugin generates the CSS.

## How it works

- `App.melt` has no `<style>` block — it uses Tailwind utilities only (so it does
  not rely on Melt's scoped CSS).
- `app.css` is `@import "tailwindcss";` + **`@source "./src/**/*.melt"`**. Melt
  keeps `class="..."` verbatim from `.melt` → generated Scala → DOM, so Tailwind
  finds the classes by scanning the `.melt` sources.
- `index.js` imports `app.css` and the Scala.js output (`scalajs:main.js`).
- Bundling is done by Vite: the `tailwindcss()` plugin plus a small local resolver
  that maps `scalajs:` to the sbt-linked output (`vite.config.mjs`, same approach
  as echarts-chart).

## Run

```bash
cd examples
sbt "tailwind-app/fastLinkJS"     # link Scala.js (ESModule)
cd tailwind-app
pnpm install                      # tailwindcss / @tailwindcss/vite / vite
npx vite                          # Node 20.19+/22.12+
```

`+` / `−` / `Reset` update the `State`, and the Tailwind-styled card responds.

> Dynamic classes work too: `class={someSignal}` or `class:the-class={condition}`
> toggle Tailwind classes reactively (in that case, make sure any dynamically
> assembled class name still appears in a file scanned by `@source`).
