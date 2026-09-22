/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.probe

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import meltkit.*
import meltkit.codec.BodyEncoder

/** `Endpoint.response[R]` が契約として強制されないことの実証。
  *
  * **このファイルがコンパイルを通ること自体が「不具合の証拠」である。**
  * `.response[User]` と宣言したエンドポイントに、`User` と無関係なテキストを返す
  * ハンドラを登録できてしまう。`on()` は `ep.responseEncoder` を使わず、ハンドラが
  * 自分で組み立てた `Response` をそのまま返すため。
  *
  * 詳細と対応方針: `memo/design-tapir-endpoint.md` §1
  *
  * 型安全化が済んだら **このファイルはコンパイルが通らなくなるはず**なので、
  * そのときに「通らないこと」を確認する回帰として書き換えること。
  */
object EndpointTypeSafetyProbe:

  case class User(id: Int, name: String)
  given BodyEncoder[User] with
    def encode(u: User): String = s"""{"id":${ u.id },"name":"${ u.name }"}"""

  /** `.response[User]` で「このエンドポイントは User を返す」と宣言している */
  val ep = Endpoint.get("users").response[User]

  val app = MeltKit[Future]()

  // ★ User を返すと宣言したのに、テキストを返せてしまうか？
  app.on(ep) { ctx =>
    Future.successful(ctx.text("これは User ではない"))
  }
