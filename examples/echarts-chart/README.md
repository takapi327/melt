# echarts-chart

npm の [ECharts](https://echarts.apache.org/) を `@JSImport` で読み込み、Melt コンポーネント内で
チャートを描画・リアクティブ更新するサンプルです。「任意の npm/JS ライブラリを `.melt` から使う」
方法を示します。

## ポイント

- `Chart.melt` の **`<script lang="scala" module>`** に ECharts の `@JSImport` ファサードを定義。
  モジュールスクリプトは生成コードの `object Chart` 直下（=トップレベル）に出るため、
  `@js.native @JSImport("echarts", ...)` を `.melt` 内に置けます。
- チャート DOM は `bind:this={chartRef}` で参照し、`onMount` で `ECharts.init(el).setOption(...)`。
- `State[List[Int]]` の変化を `effect` で購読し、`setOption` で再描画（"Randomize" ボタン）。
- バンドルは **Vite**。エントリ `index.js` の `import "scalajs:main.js"` を `vite.config.mjs` の
  小さな自作リゾルバが sbt リンク済み出力へ解決し、Scala.js が出す `import "echarts"` は
  Vite が `node_modules` から解決します（webpack / scalajs-bundler は不要）。

## 実行

```bash
# 1. npm 依存（echarts / vite / @melt/vite-plugin）をインストール
pnpm install

# 2. Scala.js をリンク（dev は fastLinkJS。ESModule 出力）
sbt "echarts-chart/fastLinkJS"

# 3. examples/echarts-chart で Vite dev サーバを起動（Node 20.19+/22.12+）
npx vite
```

ブラウザで棒グラフが表示され、"Randomize" で値が更新されます。
`vite build` する場合は先に `sbt "echarts-chart/fullLinkJS"` を実行してください
（リゾルバが dev=fastopt / build=opt を自動で切り替えます）。

> なぜ `@scala-js/vite-plugin-scalajs` を使わないか: 同プラグインは `sbt "print …/fullLinkJSOutput"`
> の stdout 最終行を出力パスとして読むが、**sbt 2.0.0 はパスの後に `[success]` と ANSI 制御コードを
> stdout へ出す**ため最終行がパスにならず壊れる。`vite.config.mjs` は代わりに小さな自作リゾルバで
> リンク済み出力を直接指す。
