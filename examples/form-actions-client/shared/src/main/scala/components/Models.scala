/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import melt.runtime.json.PropsCodec

import meltkit.codec.FormDataDecoder

/** The shared form-state type for the login page.
  *
  * The same type carries both the submitted input (`email`, `password`) and the
  * server's validation result (`errors`). `errors` is not part of the POST body,
  * so it decodes to an empty list — the server fills it via `copy(errors = ...)`.
  *
  *   - `derives FormDataDecoder` — parse the urlencoded POST body into this type.
  *   - `derives PropsCodec`      — serialize for hydration + the enhance envelope.
  */
case class LoginForm(email: String, password: String, errors: List[String]) derives FormDataDecoder, PropsCodec

/** The shared form-state type for the post editor (named-actions demo).
  *
  * One form with two submit buttons (`?/save`, `?/publish`) drives two named
  * actions that both operate on the same `PostForm`. `errors` carries validation
  * output; it is absent from the POST body, so it decodes to an empty list and
  * the server fills it via `copy(errors = ...)` on a validation failure.
  */
case class PostForm(title: String, body: String, errors: List[String] = Nil) derives FormDataDecoder, PropsCodec
