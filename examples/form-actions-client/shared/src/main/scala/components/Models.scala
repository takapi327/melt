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
