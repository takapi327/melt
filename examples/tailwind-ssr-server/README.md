# tailwind-ssr-server

**MeltKit[Future] Undertow サーバで Tailwind CSS を SSR + ハイドレーション**する例。
サーバが Tailwind でスタイルされた HTML を SSR し、ブラウザが hydrate してカウンターが動きます。

- コンポーネント: crossProject [`tailwind-ssr`](../tailwind-ssr) の `App.melt`。
  `State` + Tailwind ユーティリティのみ(`@JSImport` 無し)なので **JVM-safe = フル SSR+hydrate**
  (echarts のような client-only island は不要)。
- Tailwind CSS: **Tailwind CLI が `.melt` を走査して生成**した CSS を、サーバ起動時に
  テンプレートの `<head>` に inline(バンドラ不要)。
- ハイドレーション JS: 生の Scala.js ES モジュール(fastLinkJS 出力)をそのまま配信。

## 実行

```bash
cd examples
sbt "tailwind-ssrJS/fastLinkJS"                       # ハイドレーションバンドル

cd tailwind-ssr-server
pnpm install                                          # @tailwindcss/cli
pnpm exec tailwindcss -i tailwind.css \
  -o src/main/resources/generated.css                 # Tailwind CSS を生成(.melt を走査)

cd ..
sbt "tailwind-ssr-server/run"                          # http://localhost:9093
```

クラスを増減したら Tailwind CLI を再実行して `generated.css` を作り直します
(`-w` で watch 可)。

## 検証済み(curl)

- `GET /` → HTTP 200。SSR HTML の markup に Tailwind クラス
  (`min-h-screen … bg-slate-100 …`)+ `<head>` に Tailwind ルールの inline `<style>`
  (`.bg-slate-100` / `--tw-*` 変数)+ hydrate script `import("/app.js").then(m => m.hydrate?.())`。
- `/app.js`(ハイドレーションモジュール)→ HTTP 200 配信。

> Melt は `<style>` 無しでも要素にスコープクラス(`melt-xxxx`)を付けますが、Tailwind
> ユーティリティとは別クラスなので干渉しません。
