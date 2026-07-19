/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Represents a failure that occurred while decoding a request body. */
enum BodyError:

  /** The raw body could not be parsed (e.g. malformed JSON).
    *
    * `message` is a safe, generic description suitable for returning to
    * clients. `detail` holds the original library-level error message
    * (e.g. Circe's internal description) and is intended for server-side
    * logging only — never forward it to clients in production.
    */
  case DecodeError(message: String, detail: Option[String] = None)

  /** The body was parsed successfully but failed constraint validation. */
  case ValidationError(errors: List[String])

  /** Like [[ValidationError]] but with each message associated to the field it
    * came from (SvelteKit's per-field `issues()` parity). Produced by
    * [[meltkit.codec.FormDataDecoder]] so an action can surface issues next to
    * the offending input. */
  case FieldErrors(byField: Map[String, List[String]])

object BodyError:
  extension (e: BodyError)
    def message: String = e match
      case DecodeError(msg, _)   => msg
      case ValidationError(errs) => errs.mkString(", ")
      case FieldErrors(byField)  => byField.map((f, es) => s"$f: ${ es.mkString(", ") }").mkString("; ")

    /** All error messages as a flat list, regardless of the error variant. */
    def messages: List[String] = e match
      case DecodeError(msg, _)   => List(msg)
      case ValidationError(errs) => errs
      case FieldErrors(byField)  => byField.values.flatten.toList
