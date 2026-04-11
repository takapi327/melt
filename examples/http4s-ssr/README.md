# http4s-ssr — Melt SSR sample

A minimal http4s server that renders `.melt` components to HTML strings
on the JVM. No JavaScript is sent to the browser — this is a Phase A /
Phase B SSR-only demo.

## Run

```bash
sbt http4s-ssr/run
```

Then open `http://localhost:8080/` in a browser.

## Routes

| path                 | component                     | demonstrates                    |
|----------------------|-------------------------------|---------------------------------|
| `/`                  | `Home`                        | props + scoped CSS              |
| `/about`             | `About`                       | no-Props component              |
| `/todos`             | `Todos`                       | list rendering (`items.map`)    |
| `/status/:n`         | `components.Status`           | `if / else` and `match`         |

## Template

The HTML skeleton lives at `src/main/resources/index.html`. It uses
SvelteKit-style placeholders:

- `%melt.head%` — component head content + scoped CSS
- `%melt.body%` — component body
- `%melt.title%` — page title (falls back to `<melt:head><title>` if the
  server does not pass an explicit title)
- `%melt.lang%` — `<html lang="...">` value

Edit `index.html` freely to add favicons, OGP meta tags, analytics,
global CSS, or anything else you want in every page.

## Render timeout (§12.2.4)

Melt's SSR runtime already has a component-depth cap and an output-size
cap (see `docs/meltc-ssr-design.md` §12.2.1 / §12.2.2), but it does
**not** impose a wall-clock timeout on `render()` itself. A runaway
`.foreach` or infinite loop inside a `<script>` block can therefore tie
up a request thread indefinitely.

For production deployments, wrap each render in an `IO.timeout`:

```scala
import scala.concurrent.duration.*

private def renderPage[A](body: => String): IO[String] =
  IO.blocking(body).timeout(5.seconds)

val routes = HttpRoutes.of[IO] {
  case GET -> Root =>
    renderPage {
      val result = Home(Home.Props("Alice", 0))
      template.render(result, title = "Home")
    }.flatMap { html =>
      Ok(html, `Content-Type`(MediaType.text.html, Charset.`UTF-8`))
    }.handleErrorWith {
      case _: java.util.concurrent.TimeoutException =>
        InternalServerError("render timed out")
    }
}
```

Pick the threshold based on your service's SLO. Five seconds is a
conservative default; most well-behaved components should finish in
single-digit milliseconds.

Alternatively, you can run the render on a bounded blocking pool and
rely on backpressure rather than a per-request timeout — whichever
matches your operational model.

## Compile-time security warnings

The meltc compiler emits warnings for risky template patterns during
`sbt http4s-ssr/compile`:

- `<iframe src={...}>` / `<object data={...}>` / `<embed src={...}>`
  — plugin or inline content sources should be validated
- `<form action={...}>` / `<button formaction={...}>` — dynamic form
  targets can leak user input
- `<meta http-equiv="refresh" content={...}>` — open-redirect vector
- `<a target="_blank">` without `rel="noopener"` — tabnabbing

These are warnings, not errors — Phase B's `SecurityChecker` nudges
without blocking the build.
