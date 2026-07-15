/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.collection.mutable.ListBuffer
import scala.NamedTuple.AnyNamedTuple

import melt.runtime.json.PropsCodec
import melt.runtime.json.SimpleJson
import melt.runtime.render.RenderResult

import meltkit.codec.BodyDecoder
import meltkit.codec.BodyEncoder
import meltkit.codec.PathParamDecoder

/** Adapter-supplied factory that constructs a [[MeltContext]] for each request.
  *
  * @tparam F the effect type
  * @tparam C the component type for this platform
  */
private[meltkit] trait MeltContextFactory[F[_], C]:
  def build[P <: AnyNamedTuple, B](params: P, bodyDecoder: BodyDecoder[B]): MeltContext[F, P, B, C]

/** A registered route entry.
  *
  * @tparam F the effect type
  * @tparam C the component type for this platform
  */
private[meltkit] final class Route[F[_], C](
  val method:    HttpMethod,
  val segments:  List[PathSegment],
  val tryHandle: (List[String], MeltContextFactory[F, C]) => Option[() => F[Response]]
):
  /** Returns a copy of this route with `prefix` prepended to its segments. */
  private[meltkit] def withPrefix(prefix: String): Route[F, C] =
    Route(method, PathSegment.Static(prefix) :: segments, tryHandle)

/** The MeltKit routing DSL — platform-agnostic base trait.
  *
  * Users do not extend or instantiate this trait directly. Instead, use the
  * platform-specific [[MeltKit]] subclass, which fixes `C` automatically:
  *
  *   - JVM / Node.js — `MeltKit[IO]()` where `C = RenderResult`
  *   - Browser       — `MeltKit()` where `C = dom.Element`
  *
  * GET navigation routes are registered here via [[get]] / [[getAll]].
  * Data-mutation routes (`post` / `put` / `patch` / `delete`) are defined
  * in [[ServerMeltKitPlatform]] because they require server-side body access
  * and are not used by the browser router (which handles GET navigation only).
  *
  * {{{
  * // JVM / Node.js
  * val app = MeltKit[IO]()
  * app.get("api/todos") { ctx => IO.pure(ctx.ok(todos)) }
  * app.get("todos")     { ctx => IO.delay(ctx.render(TodoPage())) }
  *
  * // Browser
  * val app = MeltKit()
  * app.get("todos") { ctx => ctx.render(TodoPage()) }
  * }}}
  *
  * @tparam F the effect type (e.g. `cats.effect.IO`, `Id`)
  * @tparam C the component type for this platform
  */
trait MeltKitPlatform[F[_], C]:
  private val _routes = ListBuffer[Route[F, C]]()

  /** Returns all registered routes. Intended for adapter use only. */
  private[meltkit] def routes: List[Route[F, C]] = _routes.toList

  /** Adds a route. Used by [[ServerMeltKitPlatform]] to register typed endpoints. */
  private[meltkit] def addRoute(r: Route[F, C]): Unit = _routes += r

  private[meltkit] def register[P <: AnyNamedTuple](
    method: HttpMethod,
    spec:   PathSpec[P]
  )(handler: MeltContext[F, P, Unit, C] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, C]) => Option[() => F[Response]] =
      (rawValues, factory) =>
        val results = spec.paramDecoders.zip(rawValues).map {
          case ((_, dec), raw) =>
            dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.collect { case Right(v) => v }
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          Some(() => handler(factory.build(params, summon[BodyDecoder[Unit]])))
        else None
    _routes += Route(method, spec.segments, tryHandle)

  def get[P <: AnyNamedTuple](spec: PathSpec[P])(handler: MeltContext[F, P, Unit, C] => F[Response]): Unit =
    register("GET", spec)(handler)

  def get(path: String)(handler: MeltContext[F, PathSpec.Empty, Unit, C] => F[Response]): Unit =
    register("GET", PathSpec.fromString(path))(handler)

  /** Registers a catch-all GET handler that matches any path not already
    * matched by a more-specific route.
    *
    * Register this **last** so that specific routes (API endpoints, typed
    * pages) take precedence. Typical use is SSR page fallback:
    *
    * {{{
    * app.get("api/todos") { ctx => ... }  // specific API route first
    *
    * app.getAll { ctx =>                  // catch-all last
    *   IO.pure(ctx.melt(App()))
    * }
    * }}}
    */
  def getAll(handler: MeltContext[F, NamedTuple.Empty, Unit, C] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, C]) => Option[() => F[Response]] =
      (_, factory) => Some(() => handler(factory.build(PathSpec.emptyValue, summon[BodyDecoder[Unit]])))
    _routes += Route("GET", List(PathSegment.Wildcard), tryHandle)

  /** Mounts a sub-router under a static path prefix.
    *
    * {{{
    * val api = MeltKit[IO]()
    * api.get("users") { ctx => ... }
    *
    * app.route("api", api)  // → GET /api/users
    * }}}
    */
  def route(prefix: String, sub: MeltKitPlatform[F, C]): Unit =
    sub.routes.foreach { r => _routes += r.withPrefix(prefix) }

/** Server-specific extension of [[MeltKitPlatform]] that adds data-mutation
  * routes (`post` / `put` / `patch` / `delete`), typed endpoint support
  * via [[on]], and page route registration with [[PageOptions]] for SSG.
  *
  * Extended by the JVM and Node.js platform [[MeltKit]] subclasses only.
  * Browser routing handles GET navigation only, so these methods are
  * intentionally absent from the browser [[MeltKit]].
  *
  * Handlers registered here receive a [[ServerMeltContext]] which provides
  * access to the request body (`ctx.body.json[A]`, `ctx.body.text`, …),
  * cookies (`ctx.cookie`), and headers (`ctx.header`).
  *
  * {{{
  * val app = MeltKit[IO]()
  *
  * // GET route with SSG prerender options
  * val On = PageOptions(prerender = PrerenderOption.On)
  * app.get("about", On) { ctx => IO.delay(ctx.render(AboutPage())) }
  *
  * // Simple route with body access
  * app.post("api/todos") { ctx =>
  *   ctx.body.json[CreateTodo].flatMap {
  *     case Right(todo) => todoStore.create(todo).map(ctx.created(_))
  *     case Left(err)   => IO.pure(ctx.badRequest(err))
  *   }
  * }
  *
  * // Typed endpoint
  * val createTodo = Endpoint.post("api/todos").body[CreateTodoBody]
  * app.on(createTodo) { ctx =>
  *   ctx.body.decodeOrBadRequest.flatMap { body =>
  *     todoStore.update(_ :+ Todo(body.text)).as(ctx.ok(todo))
  *   }
  * }
  * }}}
  */
trait ServerMeltKitPlatform[F[_]] extends MeltKitPlatform[F, RenderResult]:

  private val _hooks = ListBuffer[ServerHook[F]]()

  // ── Page Options ──────────────────────────────────────────────────────

  private val _pageOptions = scala.collection.mutable.Map[List[PathSegment], PageOptions]()

  // ── Server function registry (single-flight refresh) ──────────────────────
  // name → (argsJson, ctx) => F[Some(encoded result JSON)], or F[None] when the
  // argument fails to decode (that refresh is then skipped, not surfaced as an
  // invalid update). ONLY queries are registered — never commands — so a client
  // cannot name a mutation in a refresh list and have it run.
  private val _serverFnImpls =
    scala.collection.mutable
      .Map[String, (String, ServerMeltContext[F, PathSpec.Empty, Any, RenderResult]) => F[Option[String]]]()

  // Every served function name, for duplicate-registration detection.
  private val _serverFnNames = scala.collection.mutable.Set.empty[String]

  /** 415 for a server-function request whose Content-Type is not JSON. */
  private val unsupportedMediaType: Response =
    PlainResponse(415, "text/plain; charset=utf-8", "Server functions require Content-Type: application/json")

  private def isJsonRequest(ctx: ServerMeltContext[F, PathSpec.Empty, ?, RenderResult]): Boolean =
    ctx.header("content-type").exists(_.split(";")(0).trim.equalsIgnoreCase("application/json"))

  /** Returns the [[PageOptions]] for a route registered with a [[PageOptions]] argument, if any. */
  def pageOptionsFor(segments: List[PathSegment]): Option[PageOptions] =
    _pageOptions.get(segments)

  // ── GET with PageOptions ───────────────────────────────────────────────

  /** Registers a GET route with [[PageOptions]] (e.g. for SSG prerendering).
    *
    * {{{
    * val On = PageOptions(prerender = PrerenderOption.On)
    * app.get("about", On) { ctx => IO.delay(ctx.render(AboutPage())) }
    * app.get(lang / "guide" / guide, On.copy(entries = ...)) { ctx => ... }
    * }}}
    */
  def get[P <: AnyNamedTuple](spec: PathSpec[P], options: PageOptions)(
    handler: MeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    _pageOptions(spec.segments) = options
    get(spec)(handler)

  /** Registers a GET route with a string path and [[PageOptions]]. */
  def get(path: String, options: PageOptions)(
    handler: MeltContext[F, NamedTuple.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    val spec = PathSpec.fromString(path)
    _pageOptions(spec.segments) = options
    get(spec)(handler)

  // var + Option: handlers are single (overwrite), unlike hooks (accumulate).
  private var _notFoundHandler: Option[MeltContext[F, NamedTuple.Empty, Unit, RenderResult] => F[Response]] = None
  private var _errorHandler: Option[(MeltContext[F, NamedTuple.Empty, Unit, RenderResult], Throwable) => F[Response]] =
    None

  /** Registers a hook to run around every matched route handler.
    *
    * Hooks run in registration order (first registered = outermost).
    *
    * {{{
    * app.use { (event, resolve) =>
    *   event.cookie("session_id") match
    *     case None     => IO.pure(Unauthorized())
    *     case Some(_)  => resolve()
    * }
    * }}}
    */
  def use(hook: ServerHook[F]): Unit =
    _hooks += hook

  /** Registers a hook from a simple function. */
  def use(fn: (RequestEvent[F], Resolve[F]) => F[Response]): Unit =
    _hooks += ServerHook(fn)

  // ── Pages with form actions ────────────────────────────────────────────

  /** Registers a page with form actions (SvelteKit-style, progressively enhanced).
    *
    * `GET` renders the page with `form = None`. `POST` runs the named form action
    * (`?/name` in the URL query; empty string = default action) and responds
    * based on the [[ActionResult]] and on whether the request is a client
    * `use:enhance` fetch (detected via the `x-melt-enhance` header):
    *
    *   - '''native POST''' (JS off / no enhance): `Redirect` → 303, `Failure` →
    *     re-render the page with `Some(data)` + status, `Success` → re-render.
    *   - '''enhance fetch''': the `ActionResult` serialized as a JSON envelope
    *     (see [[ActionResult.toJson]]); the client updates its form state.
    *
    * The same action powers both, so validation logic is written once.
    *
    * Named actions are a partial function over `(actionName, ctx)`: each case
    * matches the `?/name` action (`""` = default) and binds the context, so all
    * cases share one flat block. Single-action pages use the `action` overload
    * instead — no name to match.
    *
    * {{{
    * // named actions (?/login, ?/register) — one flat partial function
    * app.page(lang / "auth")(
    *   render  = (ctx, form) => AuthPage(AuthPage.Props(lang = ctx.params.lang, form = form)),
    *   actions = {
    *     case ("login", ctx)    => ctx.body.form[LoginForm].map { ... }
    *     case ("register", ctx) => ctx.body.form[RegisterForm].map { ... }
    *   }
    * )
    * }}}
    *
    * @param render  builds the page component from the context and the optional
    *                form result (`None` on GET, `Some(data)` on POST re-render)
    * @param actions form actions as a partial function over `(name, ctx)`
    *                (`""` = default action); an unmatched name responds 400
    *
    * Action pages carry no [[PageOptions]] (they are dynamic POST handlers, not
    * prerender candidates); the four overloads are distinguished purely by path
    * type (`PathSpec` vs `String`) and by the named argument `actions` vs
    * `action`, so no overload needs a default argument.
    */
  def page[P <: AnyNamedTuple, A](spec: PathSpec[P])(
    render:  (MeltContext[F, P, Unit, RenderResult], Option[A]) => RenderResult,
    actions: PartialFunction[(String, ServerMeltContext[F, P, Unit, RenderResult]), F[ActionResult[A]]]
  )(using pure: Pure[F], functor: Functor[F], codec: PropsCodec[A]): Unit =
    registerActionPage(spec, render, ctx => actions.lift((actionKey(ctx), ctx)))

  /** [[page]] with a single default action (no named-action dispatch).
    *
    * For the common one-form page: `POST` always runs `action`, regardless of any
    * `?/name` in the query.
    *
    * {{{
    * app.page("")(
    *   render = (_, form) => LoginPage(LoginPage.Props(form = form)),
    *   action = ctx => ctx.body.form[LoginForm].map {
    *     case Right(f) if f.email.contains("@") => ActionResult.Redirect("/dashboard")
    *     case Right(f)                          => fail(422, f.copy(errors = List("invalid")))
    *     case Left(e)                           => fail(400, LoginForm("", "", List(e.message)))
    *   }
    * )
    * }}}
    */
  def page[P <: AnyNamedTuple, A](spec: PathSpec[P])(
    render: (MeltContext[F, P, Unit, RenderResult], Option[A]) => RenderResult,
    action: ServerMeltContext[F, P, Unit, RenderResult] => F[ActionResult[A]]
  )(using pure: Pure[F], functor: Functor[F], codec: PropsCodec[A]): Unit =
    registerActionPage(spec, render, ctx => Some(action(ctx)))

  /** [[page]] (named actions) with a string path (no path parameters). */
  def page[A](path: String)(
    render:  (MeltContext[F, PathSpec.Empty, Unit, RenderResult], Option[A]) => RenderResult,
    actions: PartialFunction[(String, ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult]), F[ActionResult[A]]]
  )(using pure: Pure[F], functor: Functor[F], codec: PropsCodec[A]): Unit =
    registerActionPage(PathSpec.fromString(path), render, ctx => actions.lift((actionKey(ctx), ctx)))

  /** [[page]] (single default action) with a string path (no path parameters). */
  def page[A](path: String)(
    render: (MeltContext[F, PathSpec.Empty, Unit, RenderResult], Option[A]) => RenderResult,
    action: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[ActionResult[A]]
  )(using pure: Pure[F], functor: Functor[F], codec: PropsCodec[A]): Unit =
    registerActionPage(PathSpec.fromString(path), render, ctx => Some(action(ctx)))

  /** Shared registration for the two [[page]] families: `GET` renders with
    * `form = None`; `POST` resolves the action via `dispatch` (`None` → 400) and
    * responds through [[runAction]].
    */
  private def registerActionPage[P <: AnyNamedTuple, A](
    spec:     PathSpec[P],
    render:   (MeltContext[F, P, Unit, RenderResult], Option[A]) => RenderResult,
    dispatch: ServerMeltContext[F, P, Unit, RenderResult] => Option[F[ActionResult[A]]]
  )(using pure: Pure[F], functor: Functor[F], codec: PropsCodec[A]): Unit =
    registerServer("GET", spec) { ctx =>
      pure.pure(ctx.render(render(ctx, None)))
    }
    registerServer("POST", spec) { ctx =>
      dispatch(ctx) match
        case Some(result) => runAction(ctx, result, render)
        case None         => pure.pure(Response.badRequest("Unknown form action"))
    }

  /** Maps a resolved [[ActionResult]] to a response: an `x-melt-enhance` fetch
    * gets the JSON envelope; a native POST gets a 303 redirect (`Redirect`) or a
    * re-render with the form data (`Failure` with status / `Success`).
    */
  private def runAction[P <: AnyNamedTuple, A](
    ctx:    ServerMeltContext[F, P, Unit, RenderResult],
    result: F[ActionResult[A]],
    render: (MeltContext[F, P, Unit, RenderResult], Option[A]) => RenderResult
  )(using functor: Functor[F], codec: PropsCodec[A]): F[Response] =
    functor.map(result) { r =>
      if isEnhanceRequest(ctx) then PlainResponse(200, "application/json", ActionResult.toJson(r))
      else
        r match
          case ActionResult.Redirect(loc, seeOther) =>
            if seeOther then Response.seeOther(loc) else Response.redirect(loc)
          case ActionResult.Failure(status, data) => ctx.render(render(ctx, Some(data)), status)
          case ActionResult.Success(data)         => ctx.render(render(ctx, Some(data)))
    }

  /** Extracts the named-action key from the `?/name` query convention.
    * `POST /login?/register` parses to a query param whose key is `/register`;
    * the action name is that key with the leading `/` removed (empty = default).
    */
  private def actionKey[P <: AnyNamedTuple](ctx: ServerMeltContext[F, P, Unit, RenderResult]): String =
    ctx.queryParams.keys.find(_.startsWith("/")).map(_.drop(1)).getOrElse("")

  /** True when the request is a client `use:enhance` fetch (wants a JSON envelope). */
  private def isEnhanceRequest[P <: AnyNamedTuple](ctx: ServerMeltContext[F, P, Unit, RenderResult]): Boolean =
    ctx.header("x-melt-enhance").exists(_.equalsIgnoreCase("true"))

  private[meltkit] def hooks: List[ServerHook[F]] = _hooks.toList

  // ── CSP Configuration ─────────────────────────────────────────────────

  private var _cspConfig: Option[CspConfig] = None

  /** Configures Content Security Policy nonce injection. */
  def csp(config: CspConfig): Unit = _cspConfig = Some(config)

  /** Returns the CSP configuration, if set. */
  def cspConfig: Option[CspConfig] = _cspConfig

  // ── API Routes ────────────────────────────────────────────────────────

  /** Convenient HTTP method constants for use with [[api]]. */
  val GET:    "GET"    = "GET"
  val POST:   "POST"   = "POST"
  val PUT:    "PUT"    = "PUT"
  val DELETE: "DELETE" = "DELETE"
  val PATCH:  "PATCH"  = "PATCH"

  /** Registers multiple HTTP method handlers for the same path.
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

  /** Registers a handler for requests that don't match any route.
    *
    * Only effective when using `Http4sAdapter(app, clientDistDir, manifest).routes`
    * (SSR mode). The API-only `Http4sAdapter.routes(app)` does not support
    * `ctx.render()` in the handler — use `ctx.text()` or `ctx.json()` instead.
    *
    * {{{
    * app.onNotFound { ctx =>
    *   IO.pure(ctx.render(NotFoundPage(), 404))
    * }
    * }}}
    */
  def onNotFound(handler: MeltContext[F, NamedTuple.Empty, Unit, RenderResult] => F[Response]): Unit =
    _notFoundHandler = Some(handler)

  private[meltkit] def notFoundHandler: Option[MeltContext[F, NamedTuple.Empty, Unit, RenderResult] => F[Response]] =
    _notFoundHandler

  /** Registers a handler for unhandled exceptions in route handlers.
    *
    * If the error handler itself throws, a plain-text 500 response is returned.
    *
    * {{{
    * app.onError { (ctx, error) =>
    *   IO.pure(ctx.render(ErrorPage(error.getMessage), 500))
    * }
    * }}}
    */
  def onError(handler: (MeltContext[F, NamedTuple.Empty, Unit, RenderResult], Throwable) => F[Response]): Unit =
    _errorHandler = Some(handler)

  private[meltkit] def errorHandler
    : Option[(MeltContext[F, NamedTuple.Empty, Unit, RenderResult], Throwable) => F[Response]] =
    _errorHandler

  // ── Data-mutation routes ────────────────────────────────────────────────

  private def registerServer[P <: AnyNamedTuple](
    method: HttpMethod,
    spec:   PathSpec[P]
  )(handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, RenderResult]) => Option[() => F[Response]] =
      (rawValues, factory) =>
        val results = spec.paramDecoders.zip(rawValues).map {
          case ((_, dec), raw) =>
            dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.collect { case Right(v) => v }
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          val ctx     = factory
            .build(params, summon[BodyDecoder[Unit]])
            .asInstanceOf[ServerMeltContext[F, P, Unit, RenderResult]]
          Some(() => handler(ctx))
        else None
    addRoute(Route(method, spec.segments, tryHandle))

  def post[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("POST", spec)(handler)

  def post(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("POST", PathSpec.fromString(path))(handler)

  def put[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PUT", spec)(handler)

  def put(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PUT", PathSpec.fromString(path))(handler)

  def delete[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("DELETE", spec)(handler)

  def delete(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("DELETE", PathSpec.fromString(path))(handler)

  def patch[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PATCH", spec)(handler)

  def patch(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PATCH", PathSpec.fromString(path))(handler)

  // ── Typed endpoints ─────────────────────────────────────────────────────

  /** Registers a typed endpoint handler.
    *
    * The handler must return `F[Response]` (no errorOut) or
    * `F[Either[E, Response]]` (with errorOut). Use [[MeltContext]] helpers
    * such as `ctx.ok`, `ctx.created`, and `ctx.noContent` to build responses.
    *
    * {{{
    * // without errorOut — return F[Response] directly
    * val getTodos = Endpoint.get("api/todos").response[List[Todo]]
    * app.on(getTodos) { ctx =>
    *   todoStore.get.map(ctx.ok(_))
    * }
    *
    * // with errorOut — return F[Either[E, Response]]
    * val getUser = Endpoint.get("users" / userId).errorOut[NotFound].response[User]
    * app.on(getUser) { ctx =>
    *   userStore.find(_.id == ctx.params.id) match
    *     case Some(u) => IO.pure(Right(ctx.ok(u)))
    *     case None    => IO.pure(Left(ctx.notFound("...")))
    * }
    * }}}
    */
  def on[P <: AnyNamedTuple, B, E <: Response, Out](ep: Endpoint[P, B, E, ?])(
    handler: ServerMeltContext[F, P, B, RenderResult] => F[Out]
  )(using functor: Functor[F], lift: ResponseLift[E, Out]): Unit =
    val tryHandle: (List[String], MeltContextFactory[F, RenderResult]) => Option[() => F[Response]] =
      (rawValues, factory) =>
        val results = ep.spec.paramDecoders.zip(rawValues).map {
          case ((_, dec), raw) =>
            dec.asInstanceOf[PathParamDecoder[Any]].decode(raw)
        }
        if results.forall(_.isRight) then
          val decoded = results.collect { case Right(v) => v }
          val params  = decoded.foldRight(EmptyTuple: Tuple)(_ *: _).asInstanceOf[P]
          val ctx     = factory.build(params, ep.bodyDecoder).asInstanceOf[ServerMeltContext[F, P, B, RenderResult]]
          Some(() => functor.map(handler(ctx))(lift.lift))
        else None
    addRoute(Route(ep.method, ep.spec.segments, tryHandle))

  /** Registers the server-side implementation of a server function — a
    * [[CommandFn]] or a [[QueryFn]].
    *
    * The request body is read, decoded into `In` with the function's
    * [[melt.runtime.json.PropsCodec]], and passed to `impl` along with the
    * [[ServerMeltContext]] (for session/cookies/headers). The `Out` result is
    * encoded back to JSON. A body that fails to decode yields `400 Bad Request`
    * without ever invoking `impl`.
    *
    * `impl` may reference JVM-only resources (database clients, secrets); because
    * `serve` is only ever called from server code, that implementation never
    * reaches the browser bundle.
    *
    * {{{
    * val like = ServerFn.command[PostId, Int]("posts.like")
    * app.serve(like) { (id, ctx) => postRepo.incLike(id) }
    * }}}
    */
  def serve[In, Out](fn: ServerFnContract[In, Out])(
    impl: (In, ServerMeltContext[F, PathSpec.Empty, In, RenderResult]) => F[Out]
  )(using functor: Functor[F], flatMap: FlatMap[F], pure: Pure[F], recover: Recover[F]): Unit =
    val outEnc = fn.endpoint.responseEncoder
    val inDec  = fn.endpoint.bodyDecoder

    // Fail fast on a duplicate name: routing is first-match but the refresh
    // registry is a map (last-write), so a collision would behave inconsistently.
    if !_serverFnNames.add(fn.name) then
      throw new IllegalArgumentException(
        s"Duplicate server function name: '${ fn.name }'. Each ServerFn.query/command must have a unique name."
      )

    // Register for single-flight refresh — QUERIES ONLY. A command must never be
    // reachable via the refresh registry: it re-runs a function by name with the
    // caller's context and would otherwise let a client trigger arbitrary
    // mutations (and bypass per-route hooks). A refresh whose argument fails to
    // decode yields `None` (that update is skipped, not surfaced).
    fn match
      case _: QueryFn[?, ?] =>
        _serverFnImpls(fn.name) = (argsJson, sfCtx) =>
          inDec.decode(argsJson) match
            case Right(in) =>
              functor.map(
                impl(in, sfCtx.asInstanceOf[ServerMeltContext[F, PathSpec.Empty, In, RenderResult]])
              )(out => Some(outEnc.encode(out)))
            case Left(_) => pure.pure(None)
      case _ => () // commands are not refreshable

    on(fn.endpoint) { ctx =>
      // Require application/json so a cross-site "simple request" (text/plain or
      // form-encoded, which needs no CORS preflight) cannot invoke a mutation.
      if !isJsonRequest(ctx) then pure.pure(unsupportedMediaType)
      else
        flatMap.flatMap(ctx.body.text) { raw =>
          ctx.header("x-melt-sf") match
            case Some(_) => singleFlight(fn, impl, outEnc, raw, ctx)
            case None    =>
              inDec.decode(raw) match
                case Right(in) => functor.map(impl(in, ctx))(out => ctx.ok(out)(using outEnc): Response)
                case Left(err) => pure.pure(ctx.badRequest(err): Response)
        }
    }

  /** Handles a single-flight mutation request: runs the mutation, then re-runs
    * every requested query with the same context and returns
    * `{ "result": <out>, "updates": [ { name, args, value } ] }` — so one
    * round-trip both mutates and refreshes the client's reactive queries. */
  private def singleFlight[In, Out](
    fn:     ServerFnContract[In, Out],
    impl:   (In, ServerMeltContext[F, PathSpec.Empty, In, RenderResult]) => F[Out],
    outEnc: BodyEncoder[Out],
    raw:    String,
    ctx:    ServerMeltContext[F, PathSpec.Empty, In, RenderResult]
  )(using functor: Functor[F], flatMap: FlatMap[F], pure: Pure[F], recover: Recover[F]): F[Response] =
    val envelope =
      try
        SimpleJson.parse(raw) match
          case o: SimpleJson.JsonValue.Obj => Some(o)
          case _                           => None
      catch case _: IllegalArgumentException => None

    envelope match
      case None    => pure.pure(ctx.badRequest(BodyError.DecodeError("Invalid single-flight envelope")): Response)
      case Some(o) =>
        val input = o.fields.getOrElse("input", SimpleJson.JsonValue.Null)
        val in    =
          try Right(fn.inCodec.decode(input))
          catch case _: IllegalArgumentException => Left(())
        in match
          case Left(_)   => pure.pure(ctx.badRequest(BodyError.DecodeError("Invalid request body")): Response)
          case Right(in) =>
            val refreshes = parseRefreshRequests(o)
            val sfCtx     = ctx.asInstanceOf[ServerMeltContext[F, PathSpec.Empty, Any, RenderResult]]
            flatMap.flatMap(impl(in, ctx)) { out =>
              val resultJson = outEnc.encode(out)
              // Isolate each refresh: a query that fails (or names an unknown /
              // non-query function, or has an undecodable arg) is skipped — it
              // never fails the whole request, whose mutation is already committed.
              val updateFs: List[F[Option[String]]] = refreshes.map { (name, argsJson) =>
                _serverFnImpls.get(name) match
                  case Some(h) =>
                    functor.map(recover.attempt(h(argsJson, sfCtx))) {
                      case Right(Some(v)) => Some(updateEntry(name, argsJson, v))
                      case _              => None
                    }
                  case None => pure.pure(None)
              }
              functor.map(sequenceF(updateFs)) { entries =>
                val updates = entries.flatten.mkString("[", ",", "]")
                ctx.json(s"""{"result":$resultJson,"updates":$updates}"""): Response
              }
            }

  /** Reads the `refresh` array of `{ name, args }` (args is the query's argument
    * JSON, carried as a string) from a single-flight envelope. */
  private def parseRefreshRequests(o: SimpleJson.JsonValue.Obj): List[(String, String)] =
    o.fields.get("refresh") match
      case Some(SimpleJson.JsonValue.Arr(items)) =>
        items
          .collect {
            case r: SimpleJson.JsonValue.Obj =>
              (r.getString("name"), r.getString("args"))
          }
          .collect { case (Some(n), Some(a)) => (n, a) }
      case _ => Nil

  /** Builds one update entry; `value` is already-encoded JSON, embedded raw. */
  private def updateEntry(name: String, args: String, value: String): String =
    s"""{"name":${ SimpleJson.encString(name) },"args":${ SimpleJson.encString(args) },"value":$value}"""

  private def sequenceF[A](fs: List[F[A]])(using flatMap: FlatMap[F], pure: Pure[F]): F[List[A]] =
    fs.foldRight(pure.pure(List.empty[A])) { (fa, acc) =>
      flatMap.flatMap(fa)(a => flatMap.flatMap(acc)(as => pure.pure(a :: as)))
    }

/** Extracts a [[Response]] from a handler output `Out`.
  *
  * Allows [[ServerMeltKitPlatform.on]] to accept both `F[Response]` and `F[Either[E, Response]]`:
  *
  * {{{
  * // without errorOut — F[Response]
  * app.on(getTodos) { ctx => todoStore.get.map(ctx.ok(_)) }
  *
  * // with errorOut — F[Either[E, Response]]
  * app.on(getUser) { ctx =>
  *   userStore.find(_.id == ctx.params.id) match
  *     case Some(u) => IO.pure(Right(ctx.ok(u)))
  *     case None    => IO.pure(Left(ctx.notFound("...")))
  * }
  * }}}
  */
sealed trait ResponseLift[E, Out]:
  def lift(out: Out): Response

object ResponseLift:
  /** `F[R]` where `R <: Response` — for endpoints without `errorOut`. */
  given [R <: Response]: ResponseLift[Nothing, R] with
    override def lift(r: R): Response = r

  /** `F[Either[E, R]]` where `R <: Response` — for endpoints with `errorOut`. */
  given [E <: Response, R <: Response]: ResponseLift[E, Either[E, R]] with
    override def lift(e: Either[E, R]): Response = e.fold(identity, identity)

  /** `Right[Nothing, R]` where `R <: Response` — widened from a `Right` literal. */
  given [E <: Response, R <: Response]: ResponseLift[E, Right[Nothing, R]] with
    override def lift(r: Right[Nothing, R]): Response = r.value

  /** `Left[E, Nothing]` — widened from a `Left` literal. */
  given [E <: Response]: ResponseLift[E, Left[E, Nothing]] with
    override def lift(l: Left[E, Nothing]): Response = l.value
