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

## Run it

```bash
# 1. build the client JS (from the repo root's examples build)
sbt "server-functions-client/fastLinkJS"

# 2. start the server
sbt "server-functions-server/run"
# → http://localhost:3000
```

- `/`     — reactive post list. Like (optimistic + single-flight), Delete, Reload.
- `/new`  — progressively-enhanced form with per-field validation issues.

## Layout

```
server-functions-client/          # crossProject (shared → JVM SSR + JS hydration)
  shared/src/main/scala/components/
    Models.scala                  # Post, NewPost (errors: Map[String, List[String]])
    Api.scala                     # shared ServerFn contracts
    PostsPage.melt                # query/seed + single-flight + optimistic
    NewPostPage.melt              # field-issues form (use:enhance)
server-functions/server/
  src/main/scala/server/Server.scala   # app.serve(...) + page actions
  src/main/resources/index.html
```

> Note: server-function calls (`dispatch`/`optimistic`/`run`) are JS-only and live
> inside event handlers, which are stripped from SSR output — so the shared
> components still compile for the JVM. Queries (`seeded`/`refresh`) and the
> `Async` rendering run on both platforms.
