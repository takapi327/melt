# http4s-ssr — Melt SSR + Hydration sample

A small http4s server that renders `.melt` components to HTML strings
on the JVM and (optionally) hydrates them on the client via Scala.js.
Demonstrates Phase A / B / C Melt features end-to-end against a real
http4s application.

## Layout

```
examples/http4s-ssr/
├── components/                  crossProject (JVM + JS)
│   └── shared/src/main/scala/components/
│       ├── Home.melt            props + scoped CSS (hydration)
│       ├── About.melt           no-Props component  (hydration)
│       ├── Status.melt          if/else + match     (hydration)
│       └── Todos.melt           form-driven CRUD    (SSR-only)
├── client/                      Scala.js ESModule application
│   └── src/main/scala/client/Main.scala
└── server/                      JVM http4s application
    ├── src/main/resources/index.html
    └── src/main/scala/server/Server.scala
```

- `components` compiles with `meltcHydration := true` on the JS side,
  so every `.melt` emits an `@JSExportTopLevel("hydrate", moduleID = …)`
  function.
- `client` links those per-component entries into public modules via
  `ModuleSplitStyle.SmallModulesFor(List("components"))`.
- `server` renders via `components.jvm` and serves the client's
  `fastLinkJS` output at `/assets`.

## Running

### One-shot

```bash
sbt http4s-ssr-server/run
```

The server's `run` task transparently depends on
`http4s-ssr-client / Compile / fastLinkJS`, so the client is built
(or re-built) automatically on every start. No need to invoke
`fastLinkJS` separately.

### Dev mode (auto-reload)

```bash
sbt "~http4s-ssr-server/reStart"
```

`sbt-revolver`'s `~reStart` watches every `.melt` and `.scala` file
in the dependency graph. When you save a file, sbt re-runs
`fastLinkJS`, regenerates `AssetManifest`, and restarts the server —
all in well under a second. Just hit the reload button in your
browser.

To stop the dev server:

```bash
sbt http4s-ssr-server/reStop
```

## Routes

| Path                 | Component             | Demonstrates                                   |
|----------------------|-----------------------|------------------------------------------------|
| `/`                  | `Home`                | Props + scoped CSS + hydration round-trip      |
| `/about`             | `About`               | No-Props component + hydration                 |
| `/status/:n`         | `components.Status`   | `if / else` and `match` rendering              |
| `/todos`             | `Todos`               | Form-driven CRUD, no client JS                 |
| `POST /todos/add`    | —                     | Adds a TODO, redirects to `/todos`             |
| `POST /todos/toggle/:id` | —                 | Toggles a TODO's done flag                     |
| `POST /todos/delete/:id` | —                 | Deletes a TODO                                 |
| `/assets/*`          | —                     | Scala.js `fastLinkJS` output (static files)    |

## Auto-generated `AssetManifest`

The server does **not** hardcode any `ViteManifest` entries or
filesystem paths. A `sourceGenerators` task in `build.sbt` reads the
client's `Compile / fastLinkJS` output, extracts every
`Report.PublicModule`, and emits
`target/scala-3.3.7/src_managed/main/generated/AssetManifest.scala`:

```scala
package generated

import java.io.File
import melt.runtime.ssr.ViteManifest

object AssetManifest {
  val manifest: ViteManifest = ViteManifest.fromEntries(Map(
    "scalajs:home.js"  -> ViteManifest.Entry(file = "home.js"),
    "scalajs:about.js" -> ViteManifest.Entry(file = "about.js"),
    "scalajs:status.js" -> ViteManifest.Entry(file = "status.js"),
    "scalajs:todos.js" -> ViteManifest.Entry(file = "todos.js"),
    "scalajs:main.js"  -> ViteManifest.Entry(file = "main.js")
  ))

  val clientDistDir: File = new File(
    "/path/to/examples/http4s-ssr/client/target/scala-3.3.7/http4s-ssr-client-fastopt"
  )
}
```

`Server.scala` just imports `generated.AssetManifest` and uses
`AssetManifest.manifest` / `AssetManifest.clientDistDir` directly —
adding a new `.melt` requires zero changes to the server code.

## Template

The HTML skeleton lives at `server/src/main/resources/index.html`.
It uses SvelteKit-style placeholders (`%melt.head%`, `%melt.body%`,
`%melt.title%`, `%melt.lang%`). Edit `index.html` freely to add
favicons, OGP meta tags, analytics, global CSS, or anything else you
want in every page.

## Render timeout (§12.2.4)

Melt's SSR runtime already has a component-depth cap and an
output-size cap, but does **not** impose a wall-clock timeout on
`render()` itself. For production deployments, wrap each render in
an `IO.timeout`:

```scala
import scala.concurrent.duration.*

private def renderPage[A](body: => String): IO[String] =
  IO.blocking(body).timeout(5.seconds)
```

Pick a threshold based on your SLO. Five seconds is a conservative
default; most well-behaved components should finish in single-digit
milliseconds.

## Compile-time security warnings

`meltc` emits warnings for risky template patterns during
`sbt http4s-ssr-server/compile`:

- `<iframe src={...}>` / `<object data={...}>` / `<embed src={...}>` —
  plugin or inline content sources should be validated
- `<form action={...}>` / `<button formaction={...}>` — dynamic form
  targets can leak user input
- `<meta http-equiv="refresh" content={...}>` — open-redirect vector
- `<a target="_blank">` without `rel="noopener"` — tabnabbing

These are warnings, not errors — Phase B's `SecurityChecker` nudges
without blocking the build. The `Todos.melt` in this example
intentionally uses `<form action={s"/todos/toggle/${t.id}"}>`, so
two such warnings are expected and acceptable (the URL is always a
same-origin Melt-owned path).
