# Server Functions example

Type-safe **Server Functions** end to end — the Melt take on SvelteKit remote
functions. One shared contract (`components/Api.scala`) is compiled for both the
JVM server (which implements it) and the JS client (which calls it), so input and
output types can never drift between the two sides.

## What it demonstrates

| Feature | Where |
|---|---|
| **query + seed** | `Api.list` served on the JVM; the `/` loader renders it as a prop, and `PostsPage` does `Api.list.seeded(props.posts)` → SSR shows the list, the client hydrates it with no loading flash or redundant fetch. |
| **reactive read** | `posts.state` drives a reactive list (`items.value.map(...)`) and a `Loading` indicator. |
| **command** | `Api.like` / `Api.remove` are `ServerFn.command`s implemented with `app.serve`. |
| **single-flight** | `Api.remove.dispatch(id).updates(posts).run()` mutates and refreshes the list in one round-trip. |
| **optimistic update** | the Like button bumps the count immediately, reconciles with the server value, and rolls back on failure. |
| **field issues** | `NewPost` carries `errors: Map[String, List[String]]`; the `/new` action returns per-field messages that `NewPostPage` shows next to each input. |
| **async SSR** | `AwaitPostsPage` calls `Api.list()` inside `<melt:await>`; `/await` uses `ctx.renderAsync` to resolve it on the server and seed hydration — no loader, no flash. |
| **streaming SSR** | `StreamingPage` has two independent `<melt:await>` boundaries; `/stream` uses `ctx.renderStream` to flush the shell first, then stream each resolved branch (out-of-order) for a fast first paint. |

## Run it

```bash
# from the examples build — links the hydration bundle, then starts the server
sbt "server-functions/run"
# → http://localhost:3000
```

- `/`       — reactive post list. Like (optimistic + single-flight), Delete, Reload.
- `/new`    — progressively-enhanced form with per-field validation issues.
- `/await`  — **blocking async SSR** (`ctx.renderAsync`): `<melt:await>` resolves on the server; one response.
- `/stream` — **streaming async SSR** (`ctx.renderStream`): the shell (with each `<melt:pending>`) flushes immediately, then each boundary streams in as it settles. Two boundaries with artificial delays make it visible — the faster count arrives (~0.6s) before the slower posts (~1.5s), while the shell is already on screen. `curl -N http://localhost:3000/stream` shows the chunks arrive incrementally.

## Layout

One `enablePlugins(MeltkitAppPlugin)` derives `server-functions-frontend` (JS
hydration) and `server-functions-backend` (http4s SSR). `frontend/` is compiled
twice — to JS for hydration, and inside the backend for SSR — so the shared
contract and the components live in one place.

```
server-functions/
  frontend/src/main/scala/components/   # compiled to JS *and* into the backend
    Models.scala                  # Post, NewPost (errors: Map[String, List[String]])
    Api.scala                     # shared ServerFn contracts
    PostsPage.melt                # query/seed + single-flight + optimistic
    NewPostPage.melt              # field-issues form (use:form auto-binding + use:enhance)
    AwaitPostsPage.melt           # blocking async SSR (<melt:await>, nested boundary)
    StreamingPage.melt            # streaming async SSR (two independent boundaries)
  backend/
    src/main/scala/server/Server.scala   # app.serve(...) + page actions
    src/main/resources/index.html
```

> Note: server-function calls (`dispatch`/`optimistic`/`run`) are JS-only and live
> inside event handlers, which are stripped from SSR output — so the shared
> components still compile for the JVM. Queries (`seeded`/`refresh`) and the
> `Async` rendering run on both platforms.
