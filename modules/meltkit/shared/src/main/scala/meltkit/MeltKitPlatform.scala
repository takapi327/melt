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
  /** Returns a copy of this route sitting at `to` instead of its current segments. */
  private[meltkit] def copyAt(to: List[PathSegment]): Route[F, C] =
    Route(method, to, tryHandle)

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
  /** Sub-routers mounted with [[route]], paired with where they were mounted.
    *
    * Mounts are resolved on read, not copied on `route`. Copying made mounting a snapshot:
    * anything declared on a sub-router afterwards — a route, a guard, a server function —
    * was dropped without a word.
    */
  private val _mounts = ListBuffer[(List[PathSegment], MeltKitPlatform[F, C])]()

  private[meltkit] def mounts: List[(List[PathSegment], MeltKitPlatform[F, C])] = _mounts.toList

  /** Where a mounted router's `segs` sit once mounted at `at`. */
  private[meltkit] def relocate(at: List[PathSegment], segs: List[PathSegment]): List[PathSegment] =
    at ::: segs

  private[meltkit] def routes: List[Route[F, C]] =
    _routes.toList ::: _mounts.toList.flatMap { (at, sub) =>
      sub.routes.map(r => r.copyAt(relocate(at, r.segments)))
    }

  /** Adds a route. Used by [[ServerMeltKitPlatform]] to register typed endpoints. */
  private[meltkit] def addRoute(r: Route[F, C]): Unit = _routes += r

  // ── Nested layouts ─────────────────────────────────────────────────────

  /** Layouts registered by static path prefix, in registration order. Each is a
    * `{children}` component's `apply` that wraps a child render (`() => C`) into the
    * parent, so a page under the prefix is composed inside it (SSR: `C = RenderResult`;
    * client: `C = dom.Element`). */
  private val _layouts = ListBuffer.empty[(List[PathSegment], (() => C) => C)]

  /** Registers a layout that wraps every page under `prefix`.
    *
    * `wrap` feeds the child render into a `{children}` layout component as its
    * `children` thunk, via an explicit lambda:
    * `app.layout("dashboard")(c => DashboardLayout(children = c))`
    * (props-carrying: `app.layout("dashboard")(c => DashboardLayout(props, children = c))`).
    * The empty prefix `""` wraps all pages (the root layout). Layouts nest by prefix
    * length: the shortest matching prefix is the outermost.
    *
    * On the server the composition is applied by each context's `render`; on the
    * client by the router dispatch. (Client hydration of nested layouts requires the
    * router-driven hydration entry — see the design doc.)
    */
  def layout(prefix: String)(wrap: (() => C) => C): Unit =
    _layouts += (PathSpec.prefixSegments(prefix) -> wrap)

  /** Every registered layout with its prefix segments, for [[route]] to re-scope. */
  private[meltkit] def allLayouts: List[(List[PathSegment], (() => C) => C)] =
    _layouts.toList ::: _mounts.toList.flatMap { (at, sub) =>
      sub.allLayouts.map { case (segs, wrap) => (at ::: segs, wrap) }
    }

  /** The layouts that apply to `path`, outermost first (shortest prefix first). */
  private[meltkit] def layoutsFor(path: String): List[(() => C) => C] =
    layoutsWithPrefixFor(path).map(_._2)

  /** [[layoutsFor]] paired with each layout's prefix segments, outermost first.
    * The prefix identifies a layout across paths: two paths share a layout iff its
    * prefix matches both, so the persistent-outlet nav (client) diffs these prefixes
    * to decide which mounted layouts to keep. */
  private[meltkit] def layoutsWithPrefixFor(path: String): List[(List[PathSegment], (() => C) => C)] =
    val segs = path.split('/').filter(_.nonEmpty).toList
    allLayouts
      .collect { case (lsegs, wrap) if isLayoutPrefix(lsegs, segs) => (lsegs, wrap) }
      .sortBy(_._1.length)

  /** Composes `page` inside every layout matching `path` (outermost wraps innermost).
    * With no matching layout, returns `page()` unchanged. */
  private[meltkit] def wrapLayouts(path: String, page: () => C): C =
    layoutsFor(path).foldRight(page)((wrap, inner) => () => wrap(inner))()

  private def isLayoutPrefix(prefix: List[PathSegment], path: List[String]): Boolean =
    prefix.length <= path.length &&
      prefix.zip(path).forall {
        case (PathSegment.Static(v), s) => v == s
        case _                          => true // Param/Wildcard prefixes accept any (future)
      }

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
    register("GET", PathSpec.fromRoutePath(path))(handler)

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
    * `prefix` is split on `/` like every other path string in the DSL, so `"api/v1"` mounts
    * two segments deep and `""` mounts at the root.
    *
    * {{{
    * val api = MeltKit[IO]()
    * api.get("users") { ctx => ... }
    *
    * app.route("api", api)  // → GET /api/users
    * }}}
    */
  def route(prefix: String, sub: MeltKitPlatform[F, C]): Unit =
    if sub.contains(this) then
      throw new IllegalArgumentException(
        s"Cannot mount a router at '$prefix' that already contains the router mounting it: resolving the " +
          "mount would not terminate."
      )
    _mounts += (PathSpec.prefixSegments(prefix) -> sub)

  /** True when `router` is this router or anything mounted below it. */
  private[meltkit] def contains(router: MeltKitPlatform[?, ?]): Boolean =
    (router eq this) || _mounts.exists((_, sub) => sub.contains(router))

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

  /** Hooks paired with the path area they guard, or `None` for "every request".
    *
    * `use` registers `None`. [[route]] re-registers a mounted sub-router's hooks against the
    * mount prefix, so the prefix — a line the developer wrote once — is the protected area.
    *
    * Scoping to the routes the sub-router happens to declare would be more precise and is
    * the wrong trade. It answers 404 for a path that does not exist under a guarded prefix
    * and 403 for one that does, which lets an unauthenticated caller enumerate the protected
    * surface; it also moves the security boundary every time a route is added or removed;
    * and it takes the choice away from the app, which can no longer answer 404 to hide that
    * the area exists at all. The prefix is coarser and fails closed.
    */
  private val _hooks = ListBuffer[() => List[(Option[List[PathSegment]], ServerHook[F])]]()

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

  /** A `(name, argsJson) => F[Option[encoded result JSON]]` closure over the given
    * request context, for async-SSR in-process query resolution (`<melt:await>`).
    * Reuses the query registry populated by `serve`; an unregistered name yields
    * `None`. Adapter-facing because a component has no reference to the app.
    *
    * Deliberately limited to this router's own registry — it does not reach into mounted
    * routers, matching the single-flight refresh path. In-process resolution runs no hooks,
    * so reaching across a mount would let a page render a query that a mounted router's
    * guard was written to protect. A mounted router's queries stay reachable over HTTP,
    * where its guards do run. */
  private[meltkit] def resolveQueryFn(
    ctx: ServerMeltContext[F, PathSpec.Empty, ?, RenderResult]
  )(using pure: Pure[F]): (String, String) => F[Option[String]] =
    val sfCtx = ctx.asInstanceOf[ServerMeltContext[F, PathSpec.Empty, Any, RenderResult]]
    (name, argsJson) =>
      _serverFnImpls.get(name) match
        case Some(h) => h(argsJson, sfCtx)
        case None    => pure.pure(None)

  /** 415 for a server-function request whose Content-Type is not JSON. */
  private val unsupportedMediaType: Response =
    PlainResponse(415, "text/plain; charset=utf-8", "Server functions require Content-Type: application/json")

  private def isJsonRequest(ctx: ServerMeltContext[F, PathSpec.Empty, ?, RenderResult]): Boolean =
    ctx.header("content-type").exists(_.split(";")(0).trim.equalsIgnoreCase("application/json"))

  /** Returns the [[PageOptions]] for a route registered with a [[PageOptions]] argument, if any. */
  def pageOptionsFor(segments: List[PathSegment]): Option[PageOptions] =
    _pageOptions
      .get(segments)
      .orElse(
        serverMounts.view
          .filter((at, _) => segments.startsWith(at))
          .flatMap((at, sub) => sub.pageOptionsFor(segments.drop(at.length)))
          .headOption
      )

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
    val spec = PathSpec.fromRoutePath(path)
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
    _hooks += (() => List(None -> hook))

  /** Registers a hook from a simple function. */
  def use(fn: (RequestEvent[F], Resolve[F]) => F[Response]): Unit =
    _hooks += (() => List(None -> ServerHook(fn)))

  /** Mounts a sub-router under `prefix`, carrying everything it declared.
    *
    * The base implementation moves only the routes, so a sub-router's hooks, server
    * functions, layouts and page options used to vanish at the mount without a word — a
    * guard written with `use` stopped guarding, and a `serve`d function stopped existing.
    * Each of those is re-registered here, scoped to the mount point.
    *
    * Two things are deliberately not re-scoped:
    *
    *   - Server-function routes and names stay global. Their wire path is fixed
    *     (`_melt/fn/<name>`) and the generated client calls it by name, so prefixing would
    *     move them somewhere nothing asks for. A name colliding across the mount is an
    *     error, matching `serve`'s own duplicate check. Those routes are still part of what
    *     the sub-router's own hooks cover, so a guard keeps protecting the functions its
    *     router declared.
    *   - The not-found / error handlers and the CSP / CORS config are whole-app settings
    *     an adapter reads from the served app only. There is no meaningful per-prefix
    *     reading of them, so declaring one on a sub-router is rejected at mount time rather
    *     than silently ignored.
    */
  override def route(prefix: String, sub: MeltKitPlatform[F, RenderResult]): Unit =
    sub match
      case s: ServerMeltKitPlatform[F] @unchecked =>
        rejectWholeAppSettings(prefix, s)
        rejectDuplicateServerFns(prefix, s)
        val at = PathSpec.prefixSegments(prefix)
        _hooks += (() => hooksFrom(at, s))
      case _ => ()
    super.route(prefix, sub)

  override private[meltkit] def relocate(at: List[PathSegment], segs: List[PathSegment]): List[PathSegment] =
    if segs.headOption.contains(ServerFn.reservedRoot) then segs else at ::: segs

  /** Mounted sub-routers, narrowed to the server platform. */
  private def serverMounts: List[(List[PathSegment], ServerMeltKitPlatform[F])] =
    mounts.collect { case (at, s: ServerMeltKitPlatform[F] @unchecked) => (at, s) }

  private def rejectDuplicateServerFns(prefix: String, sub: ServerMeltKitPlatform[F]): Unit =
    val clash = sub.serverFnNames.intersect(serverFnNames)
    if clash.nonEmpty then
      throw new IllegalArgumentException(
        s"Duplicate server function name: '${ clash.toList.sorted.mkString("', '") }'. Declared both on the " +
          s"router mounted at '$prefix' and on the router mounting it. Each ServerFn.query/command must have " +
          "a unique name."
      )

  private def rejectWholeAppSettings(prefix: String, sub: ServerMeltKitPlatform[F]): Unit =
    val declared = List(
      Option.when(sub.notFoundHandler.isDefined)("onNotFound"),
      Option.when(sub.errorHandler.isDefined)("onError"),
      Option.when(sub.cspConfig.isDefined)("csp"),
      Option.when(sub.corsConfig.isDefined)("cors")
    ).flatten
    if declared.nonEmpty then
      throw new IllegalArgumentException(
        s"Cannot mount a router at '$prefix' that declares ${ declared.mkString(" / ") }: these apply to " +
          "the whole app and are read from the served router only. Declare them on the router you serve."
      )

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
    registerActionPage(PathSpec.fromRoutePath(path), render, ctx => actions.lift((actionKey(ctx), ctx)))

  /** [[page]] (single default action) with a string path (no path parameters). */
  def page[A](path: String)(
    render: (MeltContext[F, PathSpec.Empty, Unit, RenderResult], Option[A]) => RenderResult,
    action: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[ActionResult[A]]
  )(using pure: Pure[F], functor: Functor[F], codec: PropsCodec[A]): Unit =
    registerActionPage(PathSpec.fromRoutePath(path), render, ctx => Some(action(ctx)))

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
          case ActionResult.Failure(status, data) => ctx.render(render(ctx, Some(data))).withStatus(status)
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

  /** Every served function name, for duplicate detection across a mount. */
  private[meltkit] def serverFnNames: Set[String] =
    _serverFnNames.toSet ++ serverMounts.flatMap((_, sub) => sub.serverFnNames)

  /** The registered hooks with the route patterns they cover, for [[route]] to re-scope. */
  private[meltkit] def scopedHooks: List[(Option[List[PathSegment]], ServerHook[F])] =
    _hooks.toList.flatMap(_())

  /** A mounted router's hooks, re-scoped to the area it was mounted into.
    *
    * An unguarded hook becomes scoped to the mount prefix; an already-scoped one has the
    * prefix prepended, so nesting composes. Server-function routes are the exception: they
    * keep a fixed wire path and never move under the prefix, so an unscoped hook is also
    * registered against each one by exact path — otherwise a router's guard would stop
    * covering the functions it declared.
    */
  private def hooksFrom(
    at:  List[PathSegment],
    sub: ServerMeltKitPlatform[F]
  ): List[(Option[List[PathSegment]], ServerHook[F])] =
    val fnPaths = sub.routes.map(_.segments).filter(_.headOption.contains(ServerFn.reservedRoot))
    sub.scopedHooks.flatMap {
      case (None, hook)       => (Some(at) -> hook) :: fnPaths.map(p => Some(p) -> hook)
      case (Some(area), hook) =>
        List(
          (if area.headOption.contains(ServerFn.reservedRoot) then Some(area) else Some(at ::: area)) -> hook
        )
    }

  /** The hooks an adapter runs, in registration order.
    *
    * A hook carried in from a mount runs for every request inside the mounted area,
    * including one that resolves to no route. A request outside the area passes straight
    * through.
    */
  private[meltkit] def hooks: List[ServerHook[F]] =
    scopedHooks.map {
      case (None, hook)       => hook
      case (Some(area), hook) =>
        new ServerHook[F]:
          def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response] =
            if PathSegment.covers(area, event.pathSegments) then hook.handle(event, resolve) else resolve()
    }

  /** True when some registered hook would run for `route` on an HTTP request.
    *
    * Static generation invokes handlers directly and runs no hooks, so a page whose router
    * is guarded would be written to disk unprotected. Prerendering has to refuse such a
    * route rather than publish what a guard exists to withhold.
    */
  /** True when some hook guards the area this request falls in.
    *
    * Adapters consult this when no route matched. A guarded area has to answer for paths
    * inside it that do not exist, or the 403/404 difference tells an unauthenticated caller
    * exactly which paths are there. When this is `false` the adapter falls through as
    * before, so a request outside every guarded area still reaches static file serving.
    */
  private[meltkit] def hooksCover(event: RequestEvent[F]): Boolean =
    scopedHooks.exists {
      case (None, _)       => true
      case (Some(area), _) => PathSegment.covers(area, event.pathSegments)
    }

  private[meltkit] def hooksApplyTo(route: Route[F, RenderResult]): Boolean =
    scopedHooks.exists {
      case (None, _)       => true
      case (Some(area), _) => area.length <= route.segments.length && area == route.segments.take(area.length)
    }

  // ── CSP Configuration ─────────────────────────────────────────────────

  private var _cspConfig: Option[CspConfig] = None

  /** Configures Content Security Policy nonce injection. */
  def csp(config: CspConfig): Unit = _cspConfig = Some(config)

  /** Returns the CSP configuration, if set. */
  def cspConfig: Option[CspConfig] = _cspConfig

  // ── CORS Configuration ────────────────────────────────────────────────

  private var _corsConfig: Option[CorsConfig] = None

  /** Configures Cross-Origin Resource Sharing. Read by every server adapter, which
    * answers preflight `OPTIONS` requests and attaches `Access-Control-*` headers.
    *
    * {{{
    * app.cors(CorsConfig(allowedOrigins = CorsOrigins.allowlist("https://app.example.com")))
    * }}}
    */
  def cors(config: CorsConfig): Unit = _corsConfig = Some(config)

  /** Returns the CORS configuration, if set. */
  def corsConfig: Option[CorsConfig] = _corsConfig

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
    registerServer("POST", PathSpec.fromRoutePath(path))(handler)

  def put[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PUT", spec)(handler)

  def put(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PUT", PathSpec.fromRoutePath(path))(handler)

  def delete[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("DELETE", spec)(handler)

  def delete(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("DELETE", PathSpec.fromRoutePath(path))(handler)

  def patch[P <: AnyNamedTuple](spec: PathSpec[P])(
    handler: ServerMeltContext[F, P, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PATCH", spec)(handler)

  def patch(path: String)(
    handler: ServerMeltContext[F, PathSpec.Empty, Unit, RenderResult] => F[Response]
  ): Unit =
    registerServer("PATCH", PathSpec.fromRoutePath(path))(handler)

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
