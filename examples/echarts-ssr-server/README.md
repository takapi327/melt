# echarts-ssr-server

**MeltKit[Future] Undertow サーバで、npm パッケージ(ECharts / `@JSImport`)を使う
ページを SSR + ハイドレーションで動かす**例。「JS ビルドで動くだけでなく、SSR して
ハイドレーションしても壊れない」ことを実証します。

- クライアント本体: crossProject [`echarts-ssr`](../echarts-ssr)(shared の `ChartPage.melt`
  + プラットフォーム分割 `ChartHost`。JVM=no-op / JS=`@JSImport("echarts")`)
- サーバ: この `MeltKit[Future]` + 内蔵 `UndertowServer`(JVM、cats-effect/http4s 不要)

## 仕組み

1. **SSR(サーバ)**: `ChartPage` の静的シェル(チャート用 `<div id="melt-echarts">`・
   初期データの fallback リスト・ボタン)を HTML 文字列で描画。`onMount` は JVM では no-op
   なので echarts には触れない。
2. **ハイドレーション(ブラウザ)**: `%melt.head%` に注入される
   `import("/chart-page.js").then(m => m.hydrate?.())` が SSR 済み DOM を hydrate し、
   `onMount` が `ChartHost.render` で echarts を描画。ボタン/`State` は生きたまま、
   `effect(data)` で再描画。
3. **echarts の解決**: dev はサーバが生の Scala.js ES モジュールを配信するのでバンドラ無し。
   `index.html` の **import map** が bare な `"echarts"` を CDN に解決する。

## 実行

```bash
cd examples
sbt "echarts-ssrJS/fastLinkJS"    # ハイドレーションバンドルをリンク
sbt "echarts-ssr-server/run"      # Undertow を :9092 で起動
# ブラウザで http://localhost:9092
```

SSR された表がまず出て、ハイドレーション後にその上の枠へ棒グラフが描画され、
"Randomize" で表とグラフの両方が更新されます。

## 検証済み(curl)

- `GET /` → HTTP 200。SSR HTML に shell・初期データ(`<li>120</li>`…)・
  hydration marker(`[melt:chart-page`)・props payload・import map・hydrate script。
- `GET /chart-page.js` ほか分割モジュール → HTTP 200。`components.-Chart-Host$.js` に
  `import * as … from "echarts"` があり、import map で解決される。

> prod ビルド(ハッシュ付きアセット + echarts をバンドル)にする場合は、`echarts-ssr` を
> Vite でビルド(`melt()` + `meltViteInputGenerate` + echarts の `resolve.alias`)して
> `dist/` と `.vite/manifest.json` を生成し、サーバの `meltkitViteDistDir` /
> `meltkitViteManifestPath` を参照させます(http4s-ssr と同じ流れ)。
