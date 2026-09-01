# zio-http SSR example

SSR + hydration served by [zio-http](https://ziohttp.com), from a single
`enablePlugins(MeltkitAppPlugin)`.

## Run it

```bash
# from the examples build — links the hydration bundle, then starts the server
sbt "zio-http-ssr/run"
# → http://localhost:9094
```

## What it shows

`MeltKit` takes a unary type constructor, and ZIO is `ZIO[R, E, A]`. The environment is kept by
passing ZIO as a type lambda, which is the only line the app has to add:

```scala
type Env    = Greeter
type App[A] = ZIO[Env, Throwable, A]

val app = MeltKit[App]()
```

Handlers then use `ZIO.serviceWithZIO` / `ZLayer` as they would in any ZIO app — Melt's core
needs no change for this, and the resulting `Routes[Env, Response]` carries the same requirement
through to `Server.serve`:

```scala
app.get("") { ctx =>
  for
    greeting <- ZIO.serviceWithZIO[Greeter](_.greet)
    visitors <- ZIO.serviceWithZIO[Greeter](_.nextVisit)
  yield ctx.render(App(App.Props(greeting = greeting, visitors = visitors)))
}

ZioHttpAdapter
  .ssrRoutes(app, new File(AssetManifest.clientDistDir), AssetManifest.manifest)
  .flatMap(Server.serve)
  .provide(Server.defaultWithPort(9094), Greeter.live)
```

`Greeter` holds a `Ref` counter, so reloading the page increments the visitor number the server
renders — the state lives in the layer, not in the component.

## Layout

One `enablePlugins(MeltkitAppPlugin)` derives `zio-http-ssr-frontend` (the hydration bundle) and
`zio-http-ssr-backend` (the zio-http server). `.autoAggregate` makes `compile` / `test` / `clean`
reach both.

```
zio-http-ssr/
  frontend/src/main/scala/components/App.melt   # compiled to JS *and* into the backend for SSR
  backend/
    src/main/scala/server/Main.scala            # ZIOAppDefault + ZioHttpAdapter.ssrRoutes
    src/main/resources/index.html               # the HTML shell (%melt.head% / %melt.body%)
```

The adapter is selected in `examples/build.sbt`:

```scala
lazy val `zio-http-ssr` = project
  .in(file("zio-http-ssr"))
  .enablePlugins(MeltkitAppPlugin)
  .autoAggregate
  .backendSettings(meltkitServerAdapter := MeltServerAdapter.ZioHttp)
```

That is the whole wiring: `meltkit` and `meltkit-adapter-zio-http` are added by the plugin, and
codegen switches to SSR mode.

> The zio-http adapter is JVM-only. zio-http publishes a Scala.js artifact, but its server driver
> (`netty/server/NettyDriver`) has no Scala.js counterpart, so `MeltMode.ZioHttp` on a Scala.js
> project fails at load time rather than producing a bundle that cannot serve.
