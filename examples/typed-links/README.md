# typed-links

**A broken internal link is a compile error, not a runtime 404.**

This full-stack example (one `enablePlugins(MeltkitAppPlugin)`) shows Melt's headline
guarantee: template links are checked against your routes *at compile time*. No database
is required — the type-safety comes from the routes, not the data source.

## The single source of truth

`frontend/src/main/scala/routes/Routes.scala` defines every route once:

```scala
object Routes:
  val list = TypedRoute.root / "users"
  val user = TypedRoute.root / "users" / param[Long]("id")
```

That one value is used at **both** ends, and the compiler keeps them in sync:

| Where | How `Routes.user` is used |
|---|---|
| `backend/.../Server.scala` | `app.get(Routes.user) { ctx => ctx.params.id }` — routing, with `id: Long` |
| `frontend/.../UserListPage.melt` | `href="/users/{u.id}"` — a link, **checked against `Routes`** |

## Wiring (one line)

`MeltkitAppPlugin` turns on `meltLinkChecking`. Point it at the routes object once, in
`examples/build.sbt`:

```scala
lazy val `typed-links` = project
  .enablePlugins(MeltkitAppPlugin)
  .sharedSettings(meltLinkCheckingRoutes := Some("routes.Routes"))
```

That's the whole setup — **no `given RouteRegistry` boilerplate**. Links compile to
`checkedRouteFor[routes.Routes.type](...)`, validated against `Routes`.

## What is checked

The Melt compiler rewrites URL-attribute interpolations (`href` / `action` / `formaction`)
into a compile-time route check. In `UserListPage.melt`:

```html
<a href="/users/{u.id}">{u.name}</a>
```

- ✅ compiles — `/users/{id}` exists and `u.id: Long` matches `param[Long]`.
- ❌ `href="/usres/{u.id}"` → `no route matches '/usres/${scala.Long}'` (typo caught).
- ❌ if `u.id` were a `String` → `no route matches '/users/${String}'` (wrong param type caught).

The check applies to internal absolute paths only. External URLs (`https://…`), protocol-relative
(`//cdn…`), fragments (`#…`), and fully-dynamic values fall back to a plain string interpolation.

## Notes

- `model.User` is a plain `case class` — swap in your own data layer freely.
- Write links in the **quoted** form `href="/users/{u.id}"`. The Scala-expression form
  `href={s"/users/${u.id}"}` is an ordinary string and is not route-checked.
- Alternative to `meltLinkCheckingRoutes`: leave it unset and instead put a
  `given RouteRegistry[routes.Routes.type] = RouteRegistry()` in the generated component's
  package. The setting form (used here) avoids that boilerplate.

## Run

```bash
sbt "typed-links/run"   # links the frontend, starts the backend → http://localhost:9096
```
