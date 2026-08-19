# meltkit-app

A full-stack **SSR + hydration** app declared with a **single** `enablePlugins`.
No hand-wired client crossProject + server project — you manage only `frontend/`
(Melt components) and `backend/` (server).

```scala
// examples/build.sbt
lazy val `meltkit-app` = project
  .in(file("meltkit-app"))
  .enablePlugins(MeltkitAppPlugin)
```

`MeltkitAppPlugin` derives two projects via sbt's `derivedProjects`:

| Derived project | Base | Role |
|---|---|---|
| `meltkit-app-frontend` | `frontend/` | Scala.js + Melt (Browser mode) — the hydration bundle |
| `meltkit-app-backend` | `backend/` | JVM server (Undertow by default). Compiles `frontend/`'s `.melt` in SSR mode + generates the asset manifest |

The Melt components in `frontend/` are compiled twice — once to JS (hydration)
and once inside the backend (SSR) — which is exactly the crossProject boilerplate
this plugin removes.

## Layout

```
meltkit-app/
├── frontend/src/main/scala/components/App.melt   # the UI (State + template)
└── backend/src/main/scala/server/Server.scala    # MeltKit[Future] + Undertow
```

## Run

One command builds the client hydration bundle **and** starts the server —
`run` on the root project links the frontend, then runs the backend:

```bash
cd examples
sbt "meltkit-app/run"     # links frontend + starts backend → http://localhost:9095
```

(Or drive the two derived projects individually:
`meltkit-app-frontend/fastLinkJS` then `meltkit-app-backend/run`.)

The counter is server-rendered and becomes interactive after hydration.

### Build mode

`root/run` links the frontend with `fastLinkJS` by default. Switch to the
optimized `fullLinkJS`:

```scala
Global / buildMode := MeltBuildMode.Full   // default: Fast
```

With `Full`, the backend's asset manifest (`clientDistDir`) also points at the
optimized `-opt` output, so the server **serves the optimized bundle** — not just
builds it. `Fast` serves the `-fastopt` output.

## Server adapter

Default is the built-in Undertow server (`MeltKit[Future]`, no cats-effect). To
use http4s instead:

```scala
Global / meltkitServerAdapter := MeltServerAdapter.Http4s
```
