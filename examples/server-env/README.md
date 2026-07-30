# Server-only env boundary example

Keep secrets on the server and expose only what is meant for the browser — enforced
by the compiler, not by convention.

## What it demonstrates

| Piece | Where |
|---|---|
| **Private env (server-only)** | `server/Server.scala` reads `PrivateEnv.optional[String]("GREETING")` and serves only a derived, non-secret greeting. `meltkit.env.PrivateEnv` exists only in the JVM artifact. |
| **The compile boundary** | `shared/components/HomePage.melt` has a commented `PrivateEnv.get(...)` line. Uncomment it and the client fails to compile — `PrivateEnv` is JVM-only, so a browser component cannot reference it. |
| **Public env (build-time, typed)** | `build.sbt` sets `meltPublicEnv := Map("appName" -> …, "apiBase" -> …)`, which generates a typed `PublicEnv` object; `HomePage.melt` reads `generated.PublicEnv.appName`. Referencing an undeclared key is a compile error. |

## Try it

```bash
# 1. build the client JS
sbt "server-envJS/fastLinkJS"

# 2. start the server (GREETING is a private env value read only on the server)
GREETING="Hi there" sbt "server-env-server/run"
# → http://localhost:8080
```

The page shows the build-time public config and the greeting the server derived from
the private `GREETING`. The secret value itself never reaches the browser — only the
non-secret string the handler chose to return.

## The boundary

Three layers, weakest to strongest:

1. `EnvChecker` — a friendly compile error if a browser component reads `sys.env` /
   `System.getenv` / `PrivateEnv.` directly (a fast, early guardrail).
2. `PrivateEnv` is JVM-only — the real boundary: referencing it from client code is a
   hard link error on the Scala.js build, which no lint or runtime check could
   guarantee.
3. `PublicEnv` is a generated whitelist — what may reach the browser is declared, so a
   typo or an undeclared key is a compile error, and nothing leaks by omission.

What is still on you: a secret passed to a hydrated component as a prop is serialized
into the page (props cross the boundary as data). Keep secrets in handlers and pass
only non-secret values in — as this example does.
