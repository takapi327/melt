# MeltKit ルーティング設計書

## 概要

MeltKit は Melt フレームワークの上に構築するフルスタックメタフレームワークです。
SvelteKit に相当する位置づけで、ルーティング・SSR・サーバー統合を担います。

### Melt エコシステムにおける位置づけ

```
Melt        .melt コンパイラ、リアクティブランタイム、SPA/SSR コード生成
MeltKit     ルーティング、サーバー統合、アダプター（本ドキュメントの対象）
```

| 対比 | Svelte | Melt |
|------|--------|------|
| コンポーネント | Svelte / `.svelte` | Melt / `.melt` |
| ビルド統合 | vite-plugin-svelte | sbt-meltc |
| フルスタック | SvelteKit | **MeltKit** |

---

## Tapir との比較・位置づけ

MeltKit は **「フロント向けの Tapir」+ 「SSR レンダリング層」** という位置づけです。

### Tapir との共通点

| 側面 | Tapir | MeltKit |
|------|-------|---------|
| 定義 | エンドポイントを型安全に定義 | ルートを型安全に定義 |
| アダプター | http4s / ZIO HTTP / Akka HTTP | http4s / ZIO HTTP / ... |
| サーバー | 持たない | 持たない |
| 型安全 | パス・クエリ・ボディを型で表現 | パス（NamedTuple）・ボディ（Iron/Circe）を型で表現 |
| 独立性 | ルート定義がフレームワーク非依存 | ルート定義がフレームワーク非依存 |

### MeltKit にしかない部分

Tapir は **API（JSON の入出力）** に特化していますが、MeltKit はそれに加えて SSR レンダリングと Vite 統合を持ちます。

```
Tapir
  型安全なエンドポイント定義 → JSON レスポンス

MeltKit  ≒  Tapir
              + SSR レンダリング（ctx.melt() で HTML を返す）
              + Vite / JS バンドル統合
              + クライアント側ハイドレーション
```

### コードで比較

```scala
// Tapir：API エンドポイント定義
val getUser = endpoint
  .get
  .in("users" / path[Int]("id"))
  .out(jsonBody[User])

// MeltKit：ページルート定義（JSON ではなく SSR された HTML を返す）
val id = param[Int :| Positive]("id")
app.get("users" / id) { ctx =>
  Database.findUser(ctx.params.id).map { user =>
    ctx.melt(UserDetailPage(UserDetailPage.Props(user = user)))
  }
}
```

HTML ページと JSON API を同じ DSL で定義できます。

```scala
// SSR ページ
app.get("users" / id) { ctx =>
  Database.findUser(ctx.params.id).map(user => ctx.melt(UserDetailPage(...)))
}

// JSON API
app.get("api" / "users" / id) { ctx =>
  Database.findUser(ctx.params.id).map(user => ctx.json(user))
}
```

---

## 設計方針

- **コードベースルーティング** — ファイルベースではなく Scala DSL でルートを定義する
- **型安全** — パスパラメーターはコンパイル時に型が確定する（NamedTuple）
- **アダプターパターン** — サーバーフレームワークへの依存を抽象化する
- **Iron / Circe との統合** — バリデーションと JSON を標準的な Scala ライブラリで行う
- **既存パターンの踏襲** — `Var[A]` / `Signal[A]` の API 設計に揃える
- **SPA 優先** — BrowserRouter を先に実装し、SSR をその上に重ねる

---

## モジュール構成

```
sbt-meltc               既存：.melt → Scala コンパイル（低レベル）
sbt-meltkit             新規：MeltKit プロジェクトのビルド統合（sbt-meltc に依存）
                               generated/MeltKitConfig.scala を自動生成

meltkit-core            ルーティング DSL・共通型・MeltContext
                        Scala 3.7+ 対象（NamedTuple stable）
meltkit-adapter-http4s  http4s アダプター（Circe / Iron 統合を含む）
meltkit-adapter-???     将来の別アダプター（ZIO HTTP、Play 等）
```

### Scala バージョン方針

| モジュール | Scala バージョン | 理由 |
|-----------|---------------|------|
| meltc / runtime（既存） | 3.3.7 LTS | 既存の互換性を維持 |
| **meltkit-core** | **3.7+** | NamedTuple stable が必要 |
| **meltkit-adapter-http4s** | **3.7+** | meltkit-core に依存 |
| sbt-meltkit | 2.12.x | sbt プラグイン要件 |

次期 LTS は **Scala 3.9（Q2 2026 予定）** で NamedTuple stable を含むため、3.9 リリース後はスムーズに移行できます。現時点では Scala 3.8.3（プロジェクト既存設定）をターゲットにします。

### sbt-meltc との関係

```
sbt-meltc    = .melt → Scala コンパイル（コンポーネントのみ使いたい人向け）
sbt-meltkit  = sbt-meltc を内部依存として使いつつ MeltKit の規約を追加
```

Svelte における `vite-plugin-svelte` と `@sveltejs/kit` の関係に相当します。

---

## ディレクトリ構造

コードベースルーティングのため、`.melt` ファイルも `.scala` ファイルも全て
`src/main/scala/` に置きます。既存の Melt の慣習と同じです。

```
src/
  main/
    scala/
      routes/
        UserRoutes.scala     ← ルート定義（.scala）
        PostRoutes.scala
        Routes.scala         ← app.route() でまとめる
      models/
        User.scala           ← ドメインモデル
      db/
        Database.scala       ← DB アクセス
      pages/                 ← .melt コンポーネント（既存の慣習通り）
        IndexPage.melt
        UserDetailPage.melt
        CreatePostPage.melt
      components/
        Nav.melt
        Footer.melt
```

---

## ルーティング DSL

Hono（JavaScript）の設計を参考に Scala へ翻訳します。

### 基本形

```scala
val app = MeltKit[IO]()

app.get("") { ctx =>
  ctx.melt(IndexPage())
}
```

### パスパラメーター

```scala
val id = param[Int :| Positive]("id")

app.get("users" / id) { ctx =>
  Database.findUser(ctx.params.id).map { user =>
    ctx.melt(UserDetailPage(UserDetailPage.Props(user = user)))
  }
}
```

### 複数パスパラメーター

```scala
val userId = param[Int :| Positive]("userId")
val postId = param[UUID]("postId")

app.get("users" / userId / "posts" / postId) { ctx =>
  // ctx.params: (userId: Int :| Positive, postId: UUID)
  Database.findPost(ctx.params.userId, ctx.params.postId).map { post =>
    ctx.melt(PostDetailPage(PostDetailPage.Props(post = post)))
  }
}
```

### リクエストボディ（POST / PUT）

`bodyValidator` をルート定義の引数に渡すパターンは採用しません。
代わりに `ctx.body[A]` が `F[Either[BodyError, A]]` を返します。

`ctx.body[A]` は `meltkit-core` 定義の `BodyDecoder[A]` 型クラスを使用します。
Circe との橋渡しは `meltkit-adapter-http4s` が提供するため、`meltkit-core` 自体は Circe に依存しません。

```scala
// ユーザーのコード（Circe given を import して使う）
import meltkit.adapter.http4s.CirceBodyDecoder.given

app.post("users") { ctx =>
  ctx.body[CreateUserBody].flatMap {
    case Right(body) =>
      Database.createUser(body).map(user => ctx.json(user))
    case Left(err) =>
      IO.pure(ctx.badRequest(err))
  }
}
```

エラーハンドリングを自分で行わない場合は convenience メソッドも用意します。

```scala
// 失敗時に自動で 400 Bad Request を返す
app.post("users") { ctx =>
  for
    body <- ctx.bodyOrBadRequest[CreateUserBody]
    user <- Database.createUser(body)
  yield ctx.json(user)
}
```

### クエリパラメーター

クエリパラメーターはパス定義には含めず、`ctx.query` で取得します。

```scala
app.get("users") { ctx =>
  val page  = ctx.query[Int :| Positive]("page")
  val limit = ctx.query[Int :| (Positive & Max[100])]("limit")

  Database.listUsers(
    page  = page.getOrElse(1),
    limit = limit.getOrElse(20)
  ).map(users => ctx.melt(UsersPage(UsersPage.Props(users = users))))
}
```

### ミドルウェア

```scala
app.use("api" / *)(authMiddleware)
```

### ネストされたルーティング

```scala
val apiRoutes = MeltKit[IO]()
apiRoutes.get("users", listUsers)
apiRoutes.post("users", createUser)

app.route("api", apiRoutes)
```

### ルート定義の分割

```scala
// src/main/scala/routes/UserRoutes.scala
object UserRoutes:
  val id     = param[Int :| Positive]("id")
  val userId = param[Int :| Positive]("userId")
  val postId = param[UUID]("postId")

  def register(app: MeltKit[IO]): Unit =
    app.get("users" / id) { ctx => ... }
    app.post("users") { ctx => ... }
    app.put("users" / id) { ctx => ... }
    app.delete("users" / id) { ctx => ... }

// src/main/scala/routes/Routes.scala
object Routes:
  def build(): MeltKit[IO] =
    val app = MeltKit[IO]()
    UserRoutes.register(app)
    PostRoutes.register(app)
    app
```

---

## パスパラメーターの型設計

### PathParam と PathSpec

```scala
// PathParam はパラメーター名を型レベルで保持する
opaque type PathParam[N <: String, A] = String

// N <: String の上界制約により "p1" 等のリテラル型が String に広げられず保持される
def param[A, N <: String](name: N): PathParam[N, A] = name

// PathSpec はセグメントのリストと NamedTuple の型を保持する
sealed trait PathSpec[P <: AnyNamedTuple]

// / 演算子で NamedTuple の型を累積する
// 注意: NamedTuple.Append は存在しない。NamedTuple.Concat を使う
extension (s: String)
  def /[N <: String, A](p: PathParam[N, A]): PathSpec[(N: A)]  = ...
  def /[P <: AnyNamedTuple](spec: PathSpec[P]): PathSpec[P]    = ...

extension [P <: AnyNamedTuple](spec: PathSpec[P])
  def /[N <: String, A](p: PathParam[N, A])
    : PathSpec[NamedTuple.Concat[P, (N: A)]] = ...  // Concat を使用
  def /(s: String): PathSpec[P] = ...
```

### NamedTuple による型安全アクセス

Scala 3.3+ の NamedTuple を使用します。

```scala
// パスの型がそのまま ctx.params の型になる
app.get("users" / id) { ctx =>
  // ctx.params: (id: Int :| Positive)
  ctx.params.id  // Int :| Positive、コンパイル時に型が確定
}

app.get("users" / userId / "posts" / postId) { ctx =>
  // ctx.params: (userId: Int :| Positive, postId: UUID)
  ctx.params.userId
  ctx.params.postId
}
```

### パスパラメーターと BodyError の住み分け

| 対象 | タイミング | 型 | 理由 |
|------|---------|-----|------|
| パスパラメーター | コンパイル時 | 型安全（Either 不要） | PathSpec の型情報から確定 |
| リクエストボディ | 実行時 | `F[Either[BodyError, A]]` | 実行時に decode するため |
| クエリパラメーター | 実行時 | `Option[A]` | 任意パラメーターのため |

---

## バリデーション

### Iron によるパスパラメーターの制約

```scala
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import io.github.iltotore.iron.constraint.string.*

val id    = param[Int :| Positive]("id")           // 正の整数
val slug  = param[String :| NonEmpty]("slug")       // 空でない文字列
val page  = param[Int :| (Positive & Max[1000])]("page")  // 1〜1000
```

### Circe + Iron によるボディバリデーション

```scala
import io.github.iltotore.iron.circe.given  // Iron × Circe 統合
import io.circe.Codec

case class CreateUserBody(
  name:  String :| (NonEmpty & MaxLength[100]),
  email: String :| ValidEmail,
  age:   Option[Int :| (Positive & Max[150])]
) derives Codec  // Circe で自動的に JSON デコード + Iron でバリデーション
```

### BodyError

```scala
enum BodyError:
  case DecodeError(message: String)           // Circe decode 失敗
  case ValidationError(errors: List[String])  // Iron 制約違反
```

バリデーション失敗時のレスポンス：

```
リクエストボディ
  ↓
Circe decode 失敗 → 400 Bad Request（decode エラー詳細）
  ↓ 成功
Iron バリデーション失敗 → 422 Unprocessable Entity（制約違反詳細）
  ↓ 成功
ハンドラー実行
```

---

## MeltContext

```scala
// meltkit-core 独自の型クラス（Circe に依存しない）
trait BodyDecoder[A]:
  def decode(body: String): Either[BodyError, A]

trait MeltContext[F[_], P <: AnyNamedTuple]:

  // パスパラメーター（コンパイル時に型安全、Either 不要）
  def params: P

  // クエリパラメーター（実行時、任意）
  def query(name: String): Option[String]
  def query[A](name: String)(using decoder: BodyDecoder[A]): Option[A]

  // リクエストボディ（meltkit-core は BodyDecoder のみ知っている）
  def body[A: BodyDecoder]: F[Either[BodyError, A]]
  def bodyOrBadRequest[A: BodyDecoder]: F[A]  // 失敗時に自動で 400

  // レスポンス
  def melt(result: RenderResult): Response[F]
  def json[A](value: A): Response[F]
  def text(value: String): Response[F]
  def badRequest(err: BodyError): Response[F]
  def notFound(message: String): Response[F]
  def redirect(path: String): Response[F]

  def request: Request[F]
```

### BodyDecoder と Circe の橋渡し

```scala
// meltkit-adapter-http4s が提供（Circe に依存するのはここだけ）
object CirceBodyDecoder:
  given [A: io.circe.Decoder]: BodyDecoder[A] with
    def decode(body: String): Either[BodyError, A] =
      io.circe.parser.decode[A](body).left.map(e => BodyError.DecodeError(e.message))
```

### 依存関係の整理

| モジュール | 依存 | 理由 |
|-----------|------|------|
| `meltkit-core` | なし | `BodyDecoder[A]` は独自型クラス、Circe 不要 |
| `meltkit-adapter-http4s` | `http4s-circe` | `given [A: Decoder]: BodyDecoder[A]` の実装 |
| ユーザーのプロジェクト | `circe` + `iron-circe` | 自分の型に `derives Codec` を付ける |

---

## アダプターパターン

`MeltKit[F]` の定義はサーバーフレームワークと独立しています。

```scala
// ルート定義はアダプターに依存しない
val app = MeltKit[IO]()
app.get("users" / id) { ctx => ... }

// アダプターに渡して HTTP ルートに変換（Template / AssetManifest は自動参照）
val http4sAdapter = Http4sAdapter[IO](app)
val routes: HttpRoutes[IO] = http4sAdapter.routes

// 将来の別アダプター（インターフェースは同じ）
val zioHttpAdapter = ZIOHttpAdapter(app)
val playAdapter    = PlayAdapter(app)
```

### sbt-meltkit による MeltKitConfig 自動生成

`Template` と `AssetManifest` はユーザーが手動で渡す必要はありません。
`sbt-meltkit` が `generated/MeltKitConfig.scala` を自動生成し、アダプターが参照します。

```scala
// sbt-meltkit が自動生成するコード（generated/MeltKitConfig.scala）
package generated

import melt.runtime.ssr.{Template, ViteManifest}

object MeltKitConfig:
  val template: Template     = Template.fromResource("/index.html")
  val manifest: ViteManifest = AssetManifest.manifest
  val basePath: String       = "/assets"
  val lang:     String       = "en"
```

### http4s アダプターの内部イメージ

```scala
class Http4sAdapter[F[_]: Async](app: MeltKit[F]):
  import generated.MeltKitConfig  // sbt-meltkit が生成したコンフィグを参照

  private def renderPage(result: RenderResult): F[Response[F]] =
    val html = MeltKitConfig.template.render(
      result,
      MeltKitConfig.manifest,
      title    = result.title.getOrElse(""),
      lang     = MeltKitConfig.lang,
      basePath = MeltKitConfig.basePath
    )
    Response.ok(html, `Content-Type`(MediaType.text.html)).pure[F]

  private val assetRoutes: HttpRoutes[F] =
    fileService[F](FileService.Config(AssetManifest.clientDistDir.getAbsolutePath))

  val routes: HttpRoutes[F] =
    assetRoutes <+> HttpRoutes.of[F]:
      case req =>
        app.dispatch(req) match
          case Some(handler) => handler(Http4sMeltContext(req, renderPage))
          case None          => NotFound()
```

---

## SPA / SSR 統合

MeltKit は **SPA を先に実装し、SSR をその上に重ねる** アプローチを取ります。

### Phase A: BrowserRouter（SPA）

クライアント側ルーティングの土台として `BrowserRouter` を `runtime/js` に追加します。

```scala
// modules/runtime/js/src/main/scala/melt/runtime/BrowserRouter.scala
object BrowserRouter:
  private val _path: Var[String] = Var(window.location.pathname)

  // ブラウザの戻る/進むに対応
  window.addEventListener("popstate", _ =>
    _path.set(window.location.pathname)
  )

  val currentPath: Signal[String] = _path.signal

  def navigate(path: String): Unit =
    window.history.pushState(null, "", path)
    _path.set(path)

  def replace(path: String): Unit =
    window.history.replaceState(null, "", path)
    _path.set(path)
```

```html
<!-- .melt コンポーネント内での使用例 -->
<script>
  val path = BrowserRouter.currentPath
</script>

{if path.value == "/" then
  <IndexPage />
else if path.value.startsWith("/users/") then
  <UserDetailPage />
else
  <NotFoundPage />
}
```

### Phase B: サーバーサイドルーティング

MeltKit DSL でルートを定義し、アダプターが HTTP リクエストを処理します。

```scala
val id = param[Int :| Positive]("id")

app.get("users" / id) { ctx =>
  Database.findUser(ctx.params.id).map { user =>
    ctx.melt(UserDetailPage(UserDetailPage.Props(user = user)))
  }
}
```

### Phase C: SSR → SPA ハイドレーション引き継ぎ

```
① ブラウザ → GET /users/42
② MeltKit がルートマッチング
③ Database.findUser(42) でデータ取得
④ JVM で UserDetailPage を SSR レンダリング
⑤ HTML + ハイドレーション Props を返却
⑥ ブラウザ：JS ロード → hydrate() でインタラクティブ化
⑦ BrowserRouter が URL を監視し、以降のナビゲーションを SPA として処理
   （サーバーへのリクエストなしにページ遷移）
```

---

## MeltKit と http4s の組み合わせ

MeltKit はフロントエンドルート（HTML ページ）を担当し、API ルートは http4s で直接定義します。
`<+>` で合成するだけなので、http4s のエコシステム（ミドルウェア・認証・ロギングなど）をそのまま活用できます。

### 役割の分担

```
MeltKit                    http4s
  GET  /                     GET    /api/users
  GET  /users                GET    /api/users/:id
  GET  /users/:id            POST   /api/users
  POST /users                PUT    /api/users/:id
                             DELETE /api/users/:id

frontendRoutes             apiRoutes
        ↓                       ↓
        └──── <+> で合成 ────────┘
                    ↓
             routes.orNotFound
                    ↓
             EmberServer
```

### コード例

```scala
import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.EmberServerBuilder
import io.circe.generic.auto.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.numeric.*
import meltkit.*
import meltkit.adapter.http4s.*

object Server extends IOApp.Simple:

  // ----------------------------------------
  // MeltKit：フロントエンドルート（HTML ページ）
  // ----------------------------------------
  val id = param[Int :| Positive]("id")

  val meltApp = MeltKit[IO]()

  meltApp.get("") { ctx =>
    ctx.melt(IndexPage())
  }

  meltApp.get("users") { ctx =>
    Database.listUsers().map { users =>
      ctx.melt(UsersPage(UsersPage.Props(users = users)))
    }
  }

  meltApp.get("users" / id) { ctx =>
    Database.findUser(ctx.params.id).map { user =>
      ctx.melt(UserDetailPage(UserDetailPage.Props(user = user)))
    }
  }

  meltApp.post("users") { ctx =>
    ctx.body[CreateUserBody].flatMap {
      case Right(body) =>
        Database.createUser(body).map(user => ctx.melt(UserDetailPage(...)))
      case Left(err) =>
        IO.pure(ctx.badRequest(err))
    }
  }

  // MeltKit → HttpRoutes[IO] に変換
  val frontendRoutes: HttpRoutes[IO] =
    Http4sAdapter[IO](meltApp).routes


  // ----------------------------------------
  // http4s：API ルート（JSON）
  // ----------------------------------------
  val apiRoutes: HttpRoutes[IO] = HttpRoutes.of[IO]:

    case GET -> Root / "api" / "users" =>
      Database.listUsers().flatMap(users => Ok(users.asJson))

    case GET -> Root / "api" / "users" / IntVar(id) =>
      Database.findUser(id).flatMap {
        case Some(user) => Ok(user.asJson)
        case None       => NotFound()
      }

    case req @ POST -> Root / "api" / "users" =>
      req.decodeJson[CreateUserBody].flatMap { body =>
        Database.createUser(body).flatMap(user => Created(user.asJson))
      }

    case req @ PUT -> Root / "api" / "users" / IntVar(id) =>
      req.decodeJson[UpdateUserBody].flatMap { body =>
        Database.updateUser(id, body).flatMap(user => Ok(user.asJson))
      }

    case DELETE -> Root / "api" / "users" / IntVar(id) =>
      Database.deleteUser(id) >> NoContent()


  // ----------------------------------------
  // ルートの合成とサーバー起動
  // ----------------------------------------
  val routes: HttpRoutes[IO] =
    frontendRoutes <+> apiRoutes   // MeltKit + http4s を <+> で合成

  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(routes.orNotFound)
      .build
      .useForever
```

### ミドルウェアも個別に適用できる

MeltKit と http4s のルートは独立しているため、それぞれ異なるミドルウェアを適用できます。

```scala
import org.http4s.server.middleware.*

// API だけに認証ミドルウェアを適用
val authedApiRoutes: HttpRoutes[IO] =
  AuthMiddleware(apiRoutes)

// フロントには CSRF ミドルウェアを適用
val csrfFrontendRoutes: HttpRoutes[IO] =
  CSRF(frontendRoutes)

val routes = csrfFrontendRoutes <+> authedApiRoutes
```

---

## Vite / Manifest 統合

### 基本方針

Melt はすでに Vite 統合の基盤を持っています。MeltKit はこれをラップして、
ユーザーが意識せずに使えるようにします。

```
既存（sbt-meltc が担当）
  fastLinkJS Report → vite-inputs.json 生成（Vite の rollupOptions.input）
  fastLinkJS Report → generated/AssetManifest.scala 生成（開発時）
  dist/.vite/manifest.json → generated/AssetManifest.scala 生成（本番時）

既存（runtime/jvm が担当）
  ViteManifest   : module ID → JS/CSS チャンク名の解決
  SsrRenderer    : 使用コンポーネントと hydration props を追跡
  Template.render: <link rel="modulepreload"> / <script type="module"> を生成

MeltKit が追加
  ctx.melt() の中で Template.render() を自動的に呼び出す
  http4s アダプターが静的ファイル配信を自動でマウント
  sbt-meltkit が AssetManifest の設定を自動化
```

### ctx.melt() の内部動作

ユーザーは `ctx.melt()` を呼ぶだけで、manifest の解決・HTML 生成・レスポンス返却が自動で行われます。

```scala
// ユーザーが書くコード
app.get("users" / id) { ctx =>
  Database.findUser(ctx.params.id).map { user =>
    ctx.melt(UserDetailPage(UserDetailPage.Props(user = user)))
  }
}

// ctx.melt() が内部でやること
def melt(result: RenderResult): Response[F] =
  val html = template.render(
    result,
    AssetManifest.manifest,  // sbt-meltkit が自動生成
    title    = result.title.getOrElse(""),
    lang     = "en",
    basePath = "/assets"
  )
  Response.ok(html, `Content-Type`(MediaType.text.html))
```

### Template.render() が生成するもの

`ctx.melt()` → `Template.render()` の中で以下が自動生成されます。

```html
<!-- head に追加（ViteManifest がチャンク依存を解決） -->
<link rel="stylesheet" href="/assets/UserDetailPage.css">
<link rel="modulepreload" href="/assets/shared.js">
<link rel="modulepreload" href="/assets/UserDetailPage.js">

<!-- body に追加（Props の JSON + ハイドレーション bootstrap） -->
<script type="application/json" data-melt-props="UserDetailPage">
  {"user":{"id":42,"name":"Alice"}}
</script>
<script type="module">
  import("/assets/UserDetailPage.js").then(m => m.hydrate())
</script>
```

### 開発 / 本番の切り替え

```
開発時 (meltcProd = false)
  fastLinkJS → AssetManifest を sbt が直接生成
  サーバーが fastLinkJS output を /assets で serve
  content-hash なし、高速ビルド

本番時 (meltcProd = true)
  npx vite build → dist/.vite/manifest.json 生成（content-hash 付きファイル名）
  JVM が manifest.json を実行時に読み込み
  サーバーが dist/ を /assets で serve
  キャッシュ最適化済みチャンク
```

### http4s アダプターによる静的ファイル配信

アダプターがアセット配信ルートを自動でマウントするため、ユーザーが設定する必要はありません。

```scala
// meltkit-adapter-http4s の内部
class Http4sAdapter[F[_]: Async](app: MeltKit[F]):
  private val assetRoutes: HttpRoutes[F] =
    fileService[F](FileService.Config(AssetManifest.clientDistDir.getAbsolutePath))

  val routes: HttpRoutes[F] =
    assetRoutes <+> appRoutes  // /assets/* + ユーザー定義ルート
```

### sbt-meltkit が担当する追加設定

`sbt-meltc` で必要だった手動設定を `sbt-meltkit` が自動化します。

```scala
// 現在（sbt-meltc）：手動設定が必要
lazy val server = project
  .settings(
    meltcAssetManifestClient := Some(client.js),  // 手動
    meltcProd                := false,             // 手動
    libraryDependencies ++= http4sDeps             // 手動
  )
  .enablePlugins(MeltcPlugin)
  .dependsOn(client.jvm)

// MeltKit（sbt-meltkit）：最小限の設定のみ
lazy val server = project
  .settings(
    meltKitClient := client.js  // これだけ
  )
  .enablePlugins(MeltKitPlugin)
  .dependsOn(client.jvm)
```

`sbt-meltkit` が内部で行うこと：

```
sbt-meltc を内部依存として使用（.melt コンパイルはそのまま）
meltcAssetManifestClient の設定を自動化
meltkit-adapter-http4s の libraryDependency を自動追加
index.html のデフォルトテンプレートを resources に自動配置
```

---

## 実装フェーズ

SPA を先に実装し、SSR をその上に重ねる順序で進めます。

### SPA フェーズ

| フェーズ | 内容 | 対象モジュール |
|---------|------|--------------|
| **Phase 1** | meltkit 向け Scala バージョンを 3.8+ に対応（NamedTuple stable の前提） | `build.sbt`, `project/Versions.scala` |
| **Phase 2** | `BrowserRouter`（History API + Var[String]、popstate 対応） | `runtime/js` |
| **Phase 3** | `MeltKit[F]`、`PathParam`、`PathSpec`、`MeltContext` の基本設計 | `meltkit-core`（Scala 3.8+） |
| **Phase 4** | NamedTuple（`NamedTuple.Concat`）による型安全パスパラメーター | `meltkit-core` |
| **Phase 5** | `BodyDecoder[A]` 型クラス + `ctx.body[A: BodyDecoder]` / `BodyError` の API 定義（Circe に依存しない独自型クラス） | `meltkit-core` |
| **Phase 6** | http4s アダプター（SPA 用）— 静的ファイル配信 + `ctx.json` / `ctx.text` / `ctx.body` の実装 + `CirceBodyDecoder` の提供（`http4s-circe` に依存） | `meltkit-adapter-http4s` |
| | **→ SPA としてアプリケーションが稼働可能** | |

### SSR フェーズ

| フェーズ | 内容 | 対象モジュール |
|---------|------|--------------|
| **Phase 7** | `generated/MeltKitConfig.scala` 自動生成（Template + ViteManifest） | `sbt-meltkit` |
| **Phase 8** | `ctx.melt()` による SSR レンダリング + ハイドレーション引き継ぎ | `meltkit-adapter-http4s` |
| | **→ SSR としてアプリケーションが稼働可能** | |

### 拡張フェーズ

| フェーズ | 内容 | 対象モジュール |
|---------|------|--------------|
| **Phase 9** | ミドルウェア（`app.use()`） | `meltkit-core` |
| **Phase 10** | 別アダプター（ZIO HTTP 等） | `meltkit-adapter-???` |

### SPA と SSR の違い

| 項目 | SPA（Phase 6 まで） | SSR（Phase 8 まで） |
|------|-------------------|-------------------|
| サーバーの役割 | 静的ファイル配信 + API | HTML 生成 + 静的ファイル配信 + API |
| `MeltKitConfig` | 不要 | 必要（sbt-meltkit が自動生成） |
| `ctx.melt()` | 不要 | 必要 |
| 初期表示 | JS ロード後にレンダリング | サーバーが HTML を返す |
| ハイドレーション | 不要 | 必要 |

### 調査で判明した実装上の注意点

| 項目 | 内容 |
|------|------|
| `NamedTuple.Append` は存在しない | `NamedTuple.Concat[P, (N: A)]` を使う |
| meltkit-core は Scala 3.7+ 必須 | NamedTuple が stable になるバージョン。プロジェクト既存の 3.8.3 を使用 |
| 次期 LTS（Scala 3.9）は Q2 2026 予定 | リリース後は 3.9 LTS に移行可能 |
| `Template` / `AssetManifest` の渡し方 | sbt-meltkit が `generated/MeltKitConfig.scala` を自動生成、アダプターが参照 |
| クライアント側ルーティング | `runtime/js` に `BrowserRouter` の新規実装が必要（History API / popstate） |
