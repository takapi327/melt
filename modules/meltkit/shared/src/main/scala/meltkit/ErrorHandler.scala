/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Handles unexpected errors in route handlers.
  *
  * {{{
  * val errorHandler: ErrorHandler[Future] = (error, event, status, message) =>
  *   val id = java.util.UUID.randomUUID().toString
  *   logger.error(s"[$id] $message", error)
  *   Future.successful(ErrorData(message, errorId = Some(id)))
  * }}}
  */
trait ErrorHandler[F[_]]:
  def handleError(error: Throwable, event: RequestEvent[F], status: Int, message: String): F[ErrorData]

/** Data returned by [[ErrorHandler]], exposed to error pages. */
case class ErrorData(
  message: String,
  errorId: Option[String]      = None,
  details: Map[String, String] = Map.empty
)
