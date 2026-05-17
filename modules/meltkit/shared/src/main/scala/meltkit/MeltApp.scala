/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.render.RenderResult

/** The main application class for building melt servers.
  *
  * Extends [[ServerMeltKitPlatform]] to inherit the existing routing DSL
  * (`get()`, `post()`, `page()`, `use()`, `on()`, etc.) and adds higher-level
  * convenience methods: `api()`, `hooks()`.
  *
  * Placed in the `meltkit` package so it can access `private[meltkit]`
  * members (`routes`, `middlewares`, `MeltContextFactory`, etc.).
  *
  * The only type-class constraints on `F[_]` are `Functor`, `Pure`, and
  * `Defer` — cats-effect is not required. `Future` works out of the box.
  */
abstract class MeltApp[F[_]] extends ServerMeltKitPlatform[F]:

  /** Convenient HTTP method constants for use with [[api]]. */
  val GET:    "GET"    = "GET"
  val POST:   "POST"   = "POST"
  val PUT:    "PUT"    = "PUT"
  val DELETE: "DELETE" = "DELETE"
  val PATCH:  "PATCH"  = "PATCH"

  // ── Server Hooks ──────────────────────────────────────────────────────

  private var _serverHooks: ServerHooks[F] = ServerHooks.empty

  /** Registers server hooks (handle + error handler).
    *
    * {{{
    * app.hooks(
    *   handle = Seq(authHook, loggingHook),
    *   handleError = Some(myErrorHandler)
    * )
    * }}}
    */
  def hooks(
    handle:      Seq[ServerHook[F]] = Nil,
    handleError: Option[ErrorHandler[F]] = None
  ): Unit =
    _serverHooks = ServerHooks(handle, handleError)

  /** Returns the registered server hooks. */
  def serverHooks: ServerHooks[F] = _serverHooks

  // ── CSP Configuration ─────────────────────────────────────────────────

  private var _cspConfig: Option[CspConfig] = None

  /** Configures Content Security Policy nonce injection. */
  def csp(config: CspConfig): Unit = _cspConfig = Some(config)

  /** Returns the CSP configuration, if set. */
  def cspConfig: Option[CspConfig] = _cspConfig

  // ── API Routes ────────────────────────────────────────────────────────

  /** Registers multiple HTTP method handlers for the same path.
    *
    * GET handlers delegate to `get()` (receives [[MeltContext]]).
    * POST/PUT/DELETE/PATCH handlers delegate to `post()`/`put()`/etc.
    * (receives [[ServerMeltContext]] at runtime via function contravariance).
    *
    * {{{
    * app.api("api/users")(
    *   GET  -> { ctx => UserService.listAll.map(ctx.ok(_)) },
    *   POST -> { ctx => ... }
    * )
    * }}}
    */
  def api[P <: AnyNamedTuple](path: PathSpec[P])(
    handlers: (HttpMethod, MeltContext[F, P, Unit, RenderResult] => F[Response])*
  ): Unit =
    handlers.foreach {
      case ("GET", h)    => get(path)(h)
      case ("POST", h)   => post(path)(h)
      case ("PUT", h)    => put(path)(h)
      case ("DELETE", h) => delete(path)(h)
      case ("PATCH", h)  => patch(path)(h)
      case _             => ()
    }
