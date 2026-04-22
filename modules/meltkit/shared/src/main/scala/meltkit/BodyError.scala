/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Represents a failure that occurred while decoding a request body. */
enum BodyError:

  /** The raw body could not be parsed (e.g. malformed JSON). */
  case DecodeError(message: String)

  /** The body was parsed successfully but failed constraint validation. */
  case ValidationError(errors: List[String])

object BodyError:
  extension (e: BodyError)
    def message: String = e match
      case DecodeError(msg)      => msg
      case ValidationError(errs) => errs.mkString(", ")
