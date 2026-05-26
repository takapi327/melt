/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.css

/** CSS 文字列を [[CssNode]] の AST にパースする再帰降下パーサー。
  *
  * セレクターの解釈・スコーピングはパーサーの責務ではない。
  * パーサーは構造 (ブロック境界と種別) のみを解析する。
  */
object CssParser:

  /** CSS 文字列を `List[CssNode]` にパースする。 */
  def parse(css: String): List[CssNode] =
    val ctx = ParseContext(css)
    parseNodes(ctx, topLevel = true)

  private def parseNodes(ctx: ParseContext, topLevel: Boolean): List[CssNode] =
    val nodes = List.newBuilder[CssNode]
    while !ctx.isEof && !(ctx.current == '}' && !topLevel) do
      ctx.skipWhitespace()
      if ctx.isEof then ()
      else if ctx.current == '}' && !topLevel then ()
      // ⚠️ 無限ループ防止: トップレベルで stray `}` に遭遇した場合は消費して続行する。
      // `parseRuleOrRaw` は `}` を stop char として停止し、空 prelude を None で返す。
      // None では位置が進まないため、ここで明示的に消費しなければ無限ループになる。
      else if ctx.current == '}' && topLevel then ctx.advance()
      else if ctx.matchComment() then
        ctx.readComment() match
          case Some(c) => nodes += CssNode.Comment(c)
          case None    => ()
      else if ctx.current == '@' then
        nodes += parseAtRule(ctx)
      else
        // セレクターまたは宣言: { が出現すればルール、なければ宣言 (RawText)
        parseRuleOrRaw(ctx) match
          case Some(n) => nodes += n
          case None    => ()
    nodes.result()

  // ── At-rule ───────────────────────────────────────────────────────────────

  private def parseAtRule(ctx: ParseContext): CssNode =
    ctx.advance() // skip '@'
    val name = ctx.readIdent()
    ctx.skipWhitespace()
    // prelude = テキストを { または ; まで読む
    val prelude = ctx.readUntil(stopChars = Set('{', ';'))
    if ctx.isEof then
      CssNode.AtRule(name, prelude.trim, body = None)
    else if ctx.current == ';' then
      ctx.advance() // skip ';'
      CssNode.AtRule(name, prelude.trim, body = None)
    else
      ctx.advance() // skip '{'
      val body =
        if CssNode.PassthroughAtRules.contains(name) then
          // @keyframes, @font-face 等: ブロック内をそのまま生テキストで保持
          List(CssNode.RawText(ctx.readRawBlock()))
        else
          // @media, @supports, @layer 等: 再帰的に内部をパース
          parseNodes(ctx, topLevel = false)
      if !ctx.isEof && ctx.current == '}' then ctx.advance()
      CssNode.AtRule(name, prelude.trim, body = Some(body))

  // ── Style rule or raw declaration ─────────────────────────────────────────

  /** セレクターと `{...}` を読み StyleRule を返す。
    * ブロックが現れなければ (EOF 等) None を返す。
    */
  private def parseRuleOrRaw(ctx: ParseContext): Option[CssNode] =
    // { が出現するまでのテキストをセレクター候補として読む
    // { が現れなければ RawText として返す
    val prelude = ctx.readUntil(stopChars = Set('{', '}'))
    if ctx.isEof || ctx.current == '}' then
      // ブロックなし — 宣言テキストとして保持
      val t = prelude.trim
      if t.isEmpty then None else Some(CssNode.RawText(t))
    else
      ctx.advance() // skip '{'
      // ブロック内を再帰パース (CSS Nesting 対応)
      val body = parseBlockBody(ctx)
      if !ctx.isEof && ctx.current == '}' then ctx.advance()
      Some(CssNode.StyleRule(prelude.trim, body))

  /** スタイルルールのブロック本体をパースする。
    *
    * CSS Nesting により、宣言 ([[CssNode.RawText]]) と
    * ネストしたルール ([[CssNode.StyleRule]] / [[CssNode.AtRule]]) が混在できる。
    *
    * 判定基準:
    *   - `{` が先に現れれば → ネストしたスタイルルール
    *   - `;` または `}` が先に現れれば → 宣言テキスト (1 宣言分だけ読む)
    *
    * ⚠️ **重要**: `;` が先に現れた場合は `readDeclaration()` で 1 宣言分のみ読む。
    * `readUntil(Set('}'))` を使うと後続の宣言・ネストルール全体を誤って吸収してしまう。
    */
  private def parseBlockBody(ctx: ParseContext): List[CssNode] =
    val nodes = List.newBuilder[CssNode]
    while !ctx.isEof && ctx.current != '}' do
      ctx.skipWhitespace()
      if ctx.isEof || ctx.current == '}' then ()
      else if ctx.matchComment() then
        ctx.readComment().foreach(c => nodes += CssNode.Comment(c))
      else if ctx.current == '@' then
        nodes += parseAtRule(ctx)
      else
        // { と ; のどちらが先に出現するかで分岐
        val peek = ctx.peekUntil(stopChars = Set('{', ';', '}'))
        peek match
          case '{' =>
            // ネストしたスタイルルール
            parseRuleOrRaw(ctx).foreach(nodes += _)
          case ';' | '}' =>
            // 宣言テキスト — 1 宣言分だけ読む (`;` を消費して終了)
            val raw = ctx.readDeclaration()
            if raw.trim.nonEmpty then nodes += CssNode.RawText(raw)
          case _ =>
            // ⚠️ 無限ループ防止: `{`, `;`, `}` がいずれも見つからずに EOF に達した場合。
            // 不正な CSS (`;` で終わらない宣言、閉じ括弧なしのブロック等) で発生する。
            // 残りテキストを `}` まで (または EOF まで) 生テキストとして消費してループを脱出する。
            val remaining = ctx.readUntil(stopChars = Set('}'))
            if remaining.trim.nonEmpty then nodes += CssNode.RawText(remaining)
    nodes.result()

// PassthroughAtRules は CssNode.PassthroughAtRules を参照 (一元定義)

/** 文字レベルスキャンのコンテキスト。 */
private class ParseContext(src: String):
  private var pos: Int = 0

  def isEof: Boolean  = pos >= src.length
  def current: Char   = src(pos)
  def advance(): Unit = pos += 1

  /** 空白をスキップ */
  def skipWhitespace(): Unit =
    while !isEof && src(pos).isWhitespace do pos += 1

  /** 識別子 (letters, digits, `-`) を読む */
  def readIdent(): String =
    val start = pos
    while !isEof && (src(pos).isLetterOrDigit || src(pos) == '-') do pos += 1
    src.substring(start, pos)

  /** `stopChars` のいずれかが現れるまでテキストを読む。
    * 文字列リテラル (`" "`, `' '`)、`url()`、CSS ブロックコメント、
    * および括弧 `()`, `[]` の内部では stopChars を無視する。
    * `{}` の内部では stopChars を無視しない (CSS Nesting で `{` を検出するため)。
    */
  def readUntil(stopChars: Set[Char]): String =
    val sb    = new StringBuilder
    var depth = 0 // () および [] のネスト深さ
    while !isEof && !(depth == 0 && stopChars.contains(src(pos))) do
      src(pos) match
        case '/' if pos + 1 < src.length && src(pos + 1) == '*' =>
          val end        = src.indexOf("*/", pos + 2)
          val commentEnd = if end < 0 then src.length else end + 2
          sb ++= src.substring(pos, commentEnd)
          pos = commentEnd
        case '"' | '\'' =>
          val q = src(pos)
          sb += src(pos)
          pos += 1
          while !isEof && src(pos) != q do
            if src(pos) == '\\' then
              sb += src(pos)
              pos += 1
            if !isEof then
              sb += src(pos)
              pos += 1
          if !isEof then
            sb += src(pos)
            pos += 1
        case 'u' if src.startsWith("url(", pos) =>
          // url(...) を括弧の深さに関わらず一括で読む
          // ⚠️ 既知の制限: url("path).css") のように引用符内に ')' があると
          // 誤って早期終了する。実際の CSS では発生しないため許容する。
          sb += src(pos)
          pos += 1
          while !isEof && src(pos) != ')' do
            sb += src(pos)
            pos += 1
          if !isEof then
            sb += src(pos)
            pos += 1
        case '(' | '[' => depth += 1; sb += src(pos); pos += 1
        case ')' | ']' => depth -= 1; sb += src(pos); pos += 1
        case c         => sb += c; pos += 1
    sb.toString

  /** 次に現れる `stopChars` のいずれかを先読みして返す (位置を変えない)。
    * どれも現れなければ `'\u0000'` を返す。
    * 文字列リテラルと `()`, `[]` 内は読み飛ばす。
    */
  def peekUntil(stopChars: Set[Char]): Char =
    var i     = pos
    var depth = 0
    while i < src.length do
      src(i) match
        case c if depth == 0 && stopChars.contains(c) => return c
        case '"' | '\'' =>
          val q = src(i); i += 1
          while i < src.length && src(i) != q do
            if src(i) == '\\' then i += 1
            i += 1
          i += 1
        case '(' | '[' => depth += 1; i += 1
        case ')' | ']' => depth -= 1; i += 1
        case '/' if i + 1 < src.length && src(i + 1) == '*' =>
          val end = src.indexOf("*/", i + 2)
          i = if end < 0 then src.length else end + 2
        case _ => i += 1
    '\u0000'

  /** 現在位置が CSS ブロックコメント開始 (`/` + `*`) かどうかを確認する (位置を変えない) */
  def matchComment(): Boolean =
    pos + 1 < src.length && src(pos) == '/' && src(pos + 1) == '*'

  /** CSS ブロックコメントを読んで内容を返す */
  def readComment(): Option[String] =
    if !matchComment() then return None
    val start = pos
    val end   = src.indexOf("*/", pos + 2)
    pos = if end < 0 then src.length else end + 2
    Some(src.substring(start, pos))

  /** 1 つの CSS 宣言 (`property: value;`) を読む。
    *
    * `{`, `;`, `}` のいずれかに達するまで読み、
    * `;` に達した場合はそれを消費して返す (`;` を結果文字列に含める)。
    * `{` または `}` に達した場合はその文字を消費せずに返す。
    *
    * これにより `parseBlockBody` が `;` の後の宣言・ネストルールを
    * 誤って吸収するバグを防ぐ。
    */
  def readDeclaration(): String =
    val text = readUntil(stopChars = Set(';', '{', '}'))
    if !isEof && src(pos) == ';' then
      pos += 1
      text + ";"
    else
      text

  /** `}` が現れるまでを生テキストとして読む (パススルーブロック用)。
    *
    * ⚠️ **既知の制限**: `{` / `}` を文字列リテラルやコメント内でも深さカウンタに加算する。
    * 例: `@keyframes test { from { content: "}"; } }` の文字列内 `}` で
    * `depth` が意図せず 0 になり、ブロックが早期終了する可能性がある。
    * ただし、実際の `.melt` CSS で `{` / `}` を含む文字列値が
    * `@keyframes` / `@font-face` 等のパススルーブロック内に現れることは極めて稀であり、
    * 実用上は問題なし。
    */
  def readRawBlock(): String =
    val sb    = new StringBuilder
    var depth = 1
    while !isEof && depth > 0 do
      if src(pos) == '{' then depth += 1
      else if src(pos) == '}' then depth -= 1
      if depth > 0 then sb += src(pos)
      pos += 1
    sb.toString
