/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import meltkit.ServerFn

object Api:

  /** The server derives this from a private env value and returns only the
    * non-secret greeting — the secret itself never crosses to the client. */
  val greeting = ServerFn.query[Unit, String]("greeting")
