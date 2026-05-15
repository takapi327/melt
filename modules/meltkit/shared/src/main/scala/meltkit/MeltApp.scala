/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

/** The main application class for building melt servers.
  *
  * Extends [[ServerMeltKitPlatform]] to inherit the existing routing DSL
  * (`get()`, `post()`, `use()`, `on()`, etc.) and adds higher-level
  * convenience methods: `page()`, `api()`, `hooks()`.
  *
  * Placed in the `meltkit` package so it can access `private[meltkit]`
  * members (`routes`, `middlewares`, `MeltContextFactory`, etc.).
  *
  * The only type-class constraints on `F[_]` are `Functor`, `Pure`, and
  * `Defer` — cats-effect is not required. `Future` works out of the box.
  *
  * {{{
  * val app = new MeltApp[Future]:
  *   page("/")(ctx => Future.successful(ctx.render(HomePage())))
  *
  *   post("api/users") { ctx =>
  *     ctx.body.json[CreateUser].flatMap(UserService.create).map(ctx.created(_))
  *   }
  * }}}
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

  // ── Page Options ──────────────────────────────────────────────────────

  private val _pageOptions = scala.collection.mutable.Map[List[PathSegment], PageOptions]()

  /** Returns the [[PageOptions]] for a route, if registered via [[page]]. */
  def pageOptionsFor(segments: List[PathSegment]): Option[PageOptions] =
    _pageOptions.get(segments)

  // ── Page Routes ───────────────────────────────────────────────────────

  /** Registers a page route (GET) with a handler.
    *
    * Delegates to `get()`, so the handler receives a [[MeltContext]].
    * `render()`, `ok()`, `json()`, `params`, and `locals` are all available.
    * For Cookie/Header access, use [[ServerHook]] or [[Middleware]] to
    * populate `locals`.
    *
    * {{{
    * app.page("users" / userId) { ctx =>
    *   UserService.findById(ctx.params.id).map(u => ctx.render(UserPage(u)))
    * }
    * }}}
    */
  def page[P <: AnyNamedTuple](path: PathSpec[P])(
    handler: MeltContext[F, P, Unit, RenderResult] => F[Response],
    options: PageOptions = PageOptions()
  ): Unit =
    _pageOptions(path.segments) = options
    get(path)(handler)

  /** Registers a page route for a static component (no data loading). */
  def page[P <: AnyNamedTuple](path: PathSpec[P])(
    component: => RenderResult
  )(using Pure[F]): Unit =
    _pageOptions(path.segments) = PageOptions()
    get(path)(ctx => Pure[F].pure(ctx.render(component)))

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
