/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.exceptions

import meltkit.BodyError

/** Raised by [[meltkit.RequestBody.decodeOrBadRequest]] when body decoding fails.
  *
  * Server adapters catch this and convert it to a 400 Bad Request response.
  */
class BodyDecodeException(val error: BodyError) extends RuntimeException(error.message)
