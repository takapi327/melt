# tailwind-app

Melt で **Tailwind CSS v4** を使う最小 SPA サンプル。`.melt` テンプレートの
`class="..."` にユーティリティクラスを書き、Vite の `@tailwindcss/vite` プラグインが
CSS を生成します。

## 仕組み

- `App.melt` は `<style>` を持たず、Tailwind ユーティリティのみで組む
  (Melt のスコープ CSS と競合しない)。
- `app.css` は `@import "tailwindcss";` + **`@source "./src/**/*.melt"`**。
  Melt は `class="..."` を `.melt` → 生成 Scala → DOM まで**逐語保持**するので、
  Tailwind は `.melt` ソースを走査してクラスを検出できる。
- `index.js` が `app.css` と Scala.js 出力(`scalajs:main.js`)を import。
- バンドルは Vite:`tailwindcss()` プラグイン + `scalajs:` を sbt リンク済み出力へ
  解決する小さな自作リゾルバ(`vite.config.mjs`、echarts-chart と同方式)。

## 実行

```bash
cd examples
sbt "tailwind-app/fastLinkJS"     # Scala.js をリンク(ESModule)
cd tailwind-app
pnpm install                      # tailwindcss / @tailwindcss/vite / vite
npx vite                          # Node 20.19+/22.12+
```

`+`/`−`/`Reset` で `State` が更新され、Tailwind でスタイルされたカードが動きます。

> 動的クラスも可能:`class={someSignal}` や `class:the-class={condition}` で
> Tailwind クラスをリアクティブに切り替えられます(その場合、動的に組み立てる
> クラス名も `@source` の走査対象ファイルに現れる形で書くこと)。
