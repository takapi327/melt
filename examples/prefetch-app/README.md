# Route prefetch example

Warm a route's query data **before** the user navigates, so the target page renders
with no loading flash — Melt's `data-melt-preload` (the SvelteKit `preload-data`
equivalent).

## What it demonstrates

| Piece | Where |
|---|---|
| **Shared server function** | `shared/components/Api.scala` — `Api.items = ServerFn.query[Unit, List[Item]]("items.list")`, compiled for both the JVM server and the JS client. |
| **Serving it (slow)** | `server/Server.scala` — `app.serve(Api.items) { … IO.sleep(800.millis) … }`, so a *cold* navigation visibly loads. |
| **Registering a prefetch** | `js/Main.scala` — `app.prefetch("items") { () => Api.items.prefetch() }`. |
| **Opting a link in** | `shared/components/Shell.melt` — `data-melt-preload="hover"` on the `<nav>` (inherited by both links). |
| **Adopting it** | `shared/components/ItemsPage.melt` — `Api.items()` starts `Done` from the prefetch cache (single-use, ~30 s TTL) instead of fetching. |

## Try it

```bash
# 1. build the client JS (from the examples build)
sbt "prefetch-appJS/fastLinkJS"

# 2. start the server
sbt "prefetch-app-server/run"
# → http://localhost:8080
```

- **Hover “Items”, then click** → the list appears immediately (the hover already
  fetched it into the prefetch cache).
- **Click “Items” without hovering** (or reload on `/items`) → you see the `Loading…`
  state for ~800 ms, then the list.

You can watch the prefetch round-trip in the browser's Network tab: hovering the link
fires `POST /_melt/fn/items.list` before you click.

## How it works

`QueryFn.prefetch(in)` fetches once into a short-lived, **single-use** cache keyed by
the query. On the next render, `Api.items()` consumes that entry and starts `Done` —
so there is no second request and no loading flash. Normal queries are untouched: the
cache is only ever populated by an explicit `prefetch`, and each entry is used at most
once (and expires after ~30 s), so no page ever serves stale data.

`data-melt-preload` accepts `hover` (default — on hover/focus), `tap` (pointer-down
only), `viewport` (as the link scrolls into view via `IntersectionObserver`), or `off`,
and is inherited from ancestor elements. Prefetch warms **data only**: a Melt SPA ships
all route code in one bundle, so there is no per-route code to preload, and prefetch
never renders the target route (it runs no effects).
