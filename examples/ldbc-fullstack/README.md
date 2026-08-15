# ldbc × Melt × MeltKit (http4s) — end-to-end type chain

Verifies one claim:

> DB スキーマ → クエリ → MeltKit エンドポイント → ルーティング → テンプレート内の式
> までが **一本の scalac の型検査で繋がっている**

Deliberately minimal: a single JVM project, no crossProject, no hydration, no
Vite. Everything that is not part of the type chain is left out.

## Run

```bash
docker compose -f examples/ldbc-fullstack/docker-compose.yml up -d
cd examples && sbt "ldbc-fullstack/run"      # http://localhost:9095
```

The `world` sample database is the standard MySQL one. If you already run ldbc's
own `docker compose` (`ldbc-mysql9.x` on port 13306) this example connects to it
as-is — the compose file here is only for a standalone run.

## The chain

| # | Link | File |
|---|---|---|
| 1 | Column types | `db/CityTable.scala` — `def population: Column[Int]` |
| 2 | Row type | same file — `(id *: name *: …).to[City]` |
| 3 | Query result | `db/CityRepository.scala` — `selectAll` carries `City` out of the schema |
| 4 | Handler / endpoint | `Server.scala` — `repo.findAll(50).map(ctx.render(…))` |
| 5 | Routing | `Server.scala` — `param[Int]("id")` → `ctx.params.id: Int` |
| 6 | Template expression | `components/*.melt` — `{c.population / 1000}` |

No layer restates the row shape. `City` is declared once and every other link
derives from it.

## Verified break tests

Each row was actually executed: edit, `sbt ldbc-fullstack/compile`, observe.

| Test | Change | Result |
|---|---|---|
| A | `Column[Int]` → `Column[String]` for `population` | ✅ `CityTable.scala:31` — *Could not prove `(Int, String, String, String, String)` is isomorphic to `models.City`* |
| B | follow A by changing `City.population` to `String` | ✅ **`CityDetailPage.melt:23:34`** — *value `/` is not a member of String* |
| C | repository returns `List[String]` instead of `List[City]` | ✅ `Server.scala:57` — *Found `List[String]`, Required `List[models.City]`* |
| D | `param[Int]("id")` → `param[String]("id")` | ✅ `Server.scala:62` — *Found `String`, Required `Int`* |
| E | rename `City.name` → `City.cityName` | ✅ **`CityDetailPage.melt:12:7`** — *value `name` is not a member of `models.City`* |

B and E are the interesting ones: the error is reported against the **`.melt`
source with line and column**, not the generated `.scala`.

## Known holes

Two things this chain does *not* catch — both worth knowing before selling it.

### 1. Column order (silent)

`.to[City]` is a **positional** tuple isomorphism, not a by-name mapping.
Swapping two columns of the same type compiles cleanly and silently maps the
wrong data:

```scala
// compiles, and is wrong
(id *: name *: district *: countryCode *: population).to[City]
```

Verified: swapping `countryCode` and `district` (both `String`) produced
`success` with no warning. Nothing downstream can detect it either, because the
types still line up.

### 2. The schema is a declaration, not the database

`CityTable` describes what the developer *believes* the table looks like. If the
real MySQL column is `VARCHAR` while the code says `Column[Int]`, nothing fails
at compile time — the mismatch surfaces at runtime on the first query. The chain
is only as true as the schema declaration, exactly like Drizzle in TypeScript.

## Note: Scala imports in `.melt`

Imports must go in the **module script**:

```html
<script lang="scala" module>
  import models.City
</script>

<script lang="scala">
  case class Props(cities: List[City])
</script>
```

The instance script body is emitted inside `apply`, while `Props` is hoisted to
the object level, so an import placed in the instance script is not in scope for
`Props`. Only the module script is emitted at object level.
