/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

/** CSS の最上位ノード。スタイルシートは `List[CssNode]` で表現する。 */
sealed trait CssNode

object CssNode:

  /** スタイルルール: `selector { ... }`
    *
    * `body` にはネストしたルールや宣言 ([[RawText]]) が混在できる (CSS Nesting)。
    */
  case class StyleRule(
    selector: String,
    body:     List[CssNode]
  ) extends CssNode

  /** At-rule: `@name prelude { ... }` または `@name prelude;`
    *
    * @param name    "media", "layer", "keyframes" など (@ を除いた名前)
    * @param prelude `{` または `;` の前のテキスト (条件・識別子等)
    * @param body    ブロック形式の場合 `Some(内部ノードリスト)`、
    *                ブロックなし (`@charset`, `@import`, `@layer base;`) の場合 `None`
    */
  case class AtRule(
    name:    String,
    prelude: String,
    body:    Option[List[CssNode]]
  ) extends CssNode

  /** 宣言ブロック内の生テキスト (プロパティ宣言・空白等)。
    *
    * `color: red;` のような宣言はパースせず文字列のまま保持する。
    * CSS スコーピングでは宣言を変換する必要がないため、この表現で十分。
    */
  case class RawText(text: String) extends CssNode

  /** `/* ... */` コメント。出力時にそのまま保持する。 */
  case class Comment(text: String) extends CssNode

  /** ブロック内に CSS ルールではなく記述子のみを持つ at-rule の名前セット。
    *
    * `CssParser` と `CssScoper` の両方が参照するため `CssNode` に一元定義する。
    * `CssParser` はこれらのブロックを `RawText` として保持し、
    * `CssScoper` は `other` のままパススルーして `scopeNodes` を呼ばない。
    *
    * 同期を取るために **両クラスが必ずここを参照する** こと。
    */
  val PassthroughAtRules: Set[String] = Set(
    "keyframes",
    "-webkit-keyframes",
    "font-face",
    "page",
    "counter-style",
    "font-palette-values",
    "property",
    "color-profile",
    "viewport"
  )
