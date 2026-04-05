# Melt

> Scala を溶かして JS にするコンパイラ

Melt は Scala.js 向けのシングルファイルコンポーネントフレームワークです。Svelte のように `.melt` ファイルに Scala・HTML・CSS を1ファイルで記述し、コンパイラが素の DOM 操作コードに変換します。

```html
<!-- Counter.melt -->
<script lang="scala">
  val count = Var(0)
  def increment(): Unit = count += 1
</script>

<div>
  <button onclick={increment}>Count: {count}</button>
</div>

<style>
button { font-size: 1.5rem; }
</style>
```

## コンセプト

- **コンパイラがフレームワーク** — Svelte と同様、ランタイムフレームワークを必要としない
- **Scala の型システムを完全保持** — テンプレート内も含め、型チェックは scalac が行う
- **ランタイムは最小限** — `melt-runtime`（約100行）の `Var` / `Signal` / `Bind` のみ

## Status

> 実験的 — Phase 0（モノレポスケルトン）

現在は開発初期フェーズです。実装フェーズの詳細は [docs/meltc-implementation-phases.md](docs/meltc-implementation-phases.md) を参照してください。

## モジュール構成

| モジュール | 説明 | 状態 |
|---|---|---|
| `meltc` | `.melt` → `.scala` コンパイラ（JVM/JS/Native） | Phase 2-3 で実装 |
| `sbt-meltc` | sbt プラグイン | Phase 3 で実装 |
| `melt-runtime` | Scala.js ランタイムライブラリ | Phase 1 で実装 |
| `melt-testing` | テストユーティリティ | Phase 10 で実装 |
| `melt-language-server` | LSP サーバー | Phase 11 で実装 |

## 開発

```bash
# ビルド
sbt compile

# テスト
sbt test

# コードフォーマット
sbt scalafmtAll
```

## ライセンス

Apache 2.0
