/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.scalajs.js
import scala.NamedTuple.AnyNamedTuple

import org.scalajs.dom

import melt.runtime.{ Hydrating, HydrationCursor, Mount }

import meltkit.codec.BodyDecoder

/** JS-side adapter that connects [[MeltKit]] route definitions to the browser's
  * History API.
  *
  * Mirrors [[meltkit.adapter.http4s.Http4sAdapter]] on the server side:
  * where the http4s adapter handles incoming HTTP requests, `BrowserAdapter`
  * handles URL changes in the browser, dispatching through the same [[MeltKit]]
  * route table.
  *
  * ==Full-replace usage==
  *
  * [[mount]] replaces the entire `rootEl` content on each navigation.
  * Use this when there is no persistent shell around the page content.
  *
  * {{{
  * object Main:
  *   def main(args: Array[String]): Unit =
  *     val rootEl = dom.document.getElementById("app")
  *     BrowserAdapter.mount(buildApp(), rootEl)
  * }}}
  *
  * ==Persistent-shell usage==
  *
  * [[mountWithShell]] renders a layout component once into `rootEl` and
  * then replaces only the `[data-melt-outlet]` element inside it on each
  * navigation. The shell (navigation bars, sidebars, …) stays in the DOM
  * and is never re-created.
  *
  * Mark the outlet in your layout template:
  *
  * {{{
  * <!-- Layout.melt -->
  * <div class="app">
  *   <nav>...</nav>
  *   <main data-melt-outlet></main>
  * </div>
  * }}}
  *
  * Then mount with the shell:
  *
  * {{{
  * val userId = param[Int]("userId")
  *
  * object Main:
  *   def main(args: Array[String]): Unit =
  *     val rootEl = dom.document.getElementById("app")
  *     val app    = MeltKit()
  *     app.get("")         { ctx => ctx.render(TodoPage()) }
  *     app.get("counter")  { ctx => ctx.render(CounterPage()) }
  *     app.get("users")    { ctx => ctx.render(UserPage()) }
  *     app.get("users" / userId) { ctx =>
  *       ctx.render(UserDetailPage(userId = ctx.params.userId))
  *     }
  *     BrowserAdapter.mountWithShell(app, rootEl, Layout())
  * }}}
  *
  * ==Link navigation==
  *
  * Plain `<a href="...">` tags are intercepted automatically — no special
  * component is needed. Same-origin links trigger client-side navigation via
  * [[Router.navigate]] without a full page reload. The following links are
  * intentionally left to the browser:
  *
  *   - Links with `rel="external"`
  *   - Links with a `target` attribute (e.g. `target="_blank"`)
  *   - Links with a `download` attribute
  *   - Links to a different origin
  *   - Non-HTTP(S) links (`mailto:`, `tel:`, …)
  *   - Clicks with modifier keys (Ctrl / Meta / Shift / Alt)
  *
  * {{{
  * <!-- No special component needed — plain <a> just works -->
  * <a href="/users/42">Alice</a>
  * <a href="https://example.com" rel="external">External site</a>
  * }}}
  *
  * To navigate programmatically, use [[Router.navigate]] directly:
  *
  * {{{
  * Router.navigate("/users/42")
  * }}}
  */
object BrowserAdapter:

  /** Mounts the [[MeltKit]] router to the browser, replacing the full `rootEl`
    * content on each navigation.
    *
    * @param app    the [[MeltKit]] router whose routes will handle URL changes
    * @param rootEl the DOM element used as the mount target for components
    */
  def mount[F[_]: AsyncRunner](app: MeltKitPlatform[F, dom.Element], rootEl: dom.Element): Unit =
    ensureLinkInterceptor()
    ensurePrefetch(app)
    val stack = new OutletStack(app, rootEl)
    dispatch(app, stack, rootEl, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, stack, rootEl, path) }

  /** Hydrates a server-rendered, router-driven app: the initial render '''claims'''
    * the existing SSR DOM (rather than replacing it), reproducing the same
    * layout+page composition the server produced; subsequent navigations render
    * normally. Call this once from the client entry, e.g.
    *
    * {{{
    * @JSExportTopLevel("hydrate", moduleID = "app")
    * def hydrate(): Unit =
    *   BrowserAdapter.hydrate(buildApp(), dom.document.getElementById("app"))
    * }}}
    *
    * The app must register the same routes and `app.layout(...)` layouts used on the
    * server, so the client composition matches the server-rendered markup.
    */
  def hydrate[F[_]: AsyncRunner](app: MeltKitPlatform[F, dom.Element], rootEl: dom.Element): Unit =
    ensureLinkInterceptor()
    ensurePrefetch(app)
    val stack = new OutletStack(app, rootEl)
    dispatch(app, stack, rootEl, Router.currentPath.value, hydrating = true)
    Router.currentPath.subscribe { path => dispatch(app, stack, rootEl, path) }

  /** Renders `shell` once into `rootEl`, then routes future navigations into
    * the `[data-melt-outlet]` element found within the shell.
    *
    * @param app    the [[MeltKit]] router whose routes will handle URL changes
    * @param rootEl the DOM element to mount the shell into
    * @param shell  the persistent shell component (e.g. `Layout()`)
    */
  def mountWithShell[F[_]: AsyncRunner](
    app:    MeltKitPlatform[F, dom.Element],
    rootEl: dom.Element,
    shell:  dom.Element
  ): Unit =
    ensureLinkInterceptor()
    ensurePrefetch(app)
    rootEl.innerHTML = ""
    Mount(rootEl, shell)
    val outlet = Option(rootEl.querySelector("[data-melt-outlet]")).getOrElse(rootEl)
    val stack  = new OutletStack(app, outlet)
    dispatch(app, stack, outlet, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, stack, outlet, path) }

  // ── Link interception ────────────────────────────────────────────────────

  private var linkInterceptorInstalled = false

  private def ensureLinkInterceptor(): Unit =
    if !linkInterceptorInstalled then
      linkInterceptorInstalled = true
      dom.document.addEventListener("click", interceptLink)

  private val interceptLink: scalajs.js.Function1[dom.MouseEvent, Unit] = event =>
    if event.button == 0
      && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey
      && !event.defaultPrevented
    then
      findAnchor(event.target).foreach { anchor =>
        internalUrlOf(anchor).foreach { url =>
          event.preventDefault()
          Router.navigate(url.pathname + (if url.search.nonEmpty then url.search else ""))
        }
      }

  /** The same-origin [[dom.URL]] a link navigates to, or `None` when it is external
    * (rel=external / target / download / cross-origin / non-http(s) / invalid). Shared
    * by click navigation and the prefetch hook. */
  private def internalUrlOf(anchor: dom.html.Anchor): Option[dom.URL] =
    val href = anchor.getAttribute("href")
    if href == null || href.isEmpty then None
    else
      val hasExternal = Option(anchor.getAttribute("rel")).exists(_.split("\\s+").contains("external"))
      val hasTarget   = Option(anchor.getAttribute("target")).exists(_.nonEmpty)
      val hasDownload = anchor.hasAttribute("download")
      if hasExternal || hasTarget || hasDownload then None
      else
        try
          val url = new dom.URL(href, dom.window.location.href)
          if (url.protocol == "https:" || url.protocol == "http:") && url.origin == dom.window.location.origin
          then Some(url)
          else None
        catch case _: Throwable => None

  private def findAnchor(target: dom.EventTarget | Null): Option[dom.html.Anchor] =
    @annotation.tailrec
    def loop(node: dom.Node | Null): Option[dom.html.Anchor] =
      if node == null then None
      else
        node match
          case a: dom.html.Anchor if a.hasAttribute("href") => Some(a)
          case _                                            => loop(node.parentNode)
    target match
      case n: dom.Node => loop(n)
      case _           => None

  // ── Link prefetch (data-melt-preload) ──────────────────────────────────────

  private var prefetchInstalled = false
  private val prefetched        = scala.collection.mutable.Set.empty[String]

  /** Installs the hover/tap/viewport prefetch hooks once, when the router is a
    * browser [[MeltKit]] (the only platform that registers `app.prefetch(...)`). */
  private def ensurePrefetch(app: MeltKitPlatform[?, dom.Element]): Unit =
    app match
      case m: MeltKit if !prefetchInstalled =>
        prefetchInstalled = true
        val onHover: scalajs.js.Function1[dom.Event, Unit] = e => maybePrefetch(m, e.target, "hover")
        dom.document.addEventListener("mouseover", onHover)
        dom.document.addEventListener("focusin", onHover)
        dom.document.addEventListener("pointerdown", (e: dom.Event) => maybePrefetch(m, e.target, "tap"))
        installViewportPrefetch(m)
      case _ => ()

  /** Runs the registered prefetch for the anchor under `target` when its
    * `data-melt-preload` mode matches the `trigger` ("hover" from mouseover/focusin,
    * "tap" from pointerdown — which also covers `hover` links on touch devices). */
  private def maybePrefetch(app: MeltKit, target: dom.EventTarget | Null, trigger: String): Unit =
    findAnchor(target).foreach { anchor =>
      val fires = preloadMode(anchor) match
        case Some("hover") => trigger == "hover" || trigger == "tap"
        case Some("tap")   => trigger == "tap"
        case _             => false // "viewport" (observer-driven), "off", or absent
      if fires then internalUrlOf(anchor).foreach(url => runPrefetch(app, url.pathname))
    }

  /** The effective `data-melt-preload` mode for `anchor` — its own attribute or the
    * nearest ancestor's (SvelteKit-style inheritance); an empty value means "hover". */
  private def preloadMode(anchor: dom.html.Anchor): Option[String] =
    @annotation.tailrec
    def loop(node: dom.Node): Option[String] =
      node match
        case el: dom.Element if el.hasAttribute("data-melt-preload") =>
          val v = el.getAttribute("data-melt-preload")
          Some(if v == null || v.isEmpty then "hover" else v)
        case _ =>
          node.parentNode match
            case null        => None
            case p: dom.Node => loop(p)
    loop(anchor)

  private def runPrefetch(app: MeltKit, pathname: String): Unit =
    if !prefetched.contains(pathname) then
      prefetched += pathname
      app.prefetchThunksFor(pathname).foreach(_())

  /** Observes `data-melt-preload="viewport"` links and prefetches each as it scrolls
    * into view. Re-scans after every navigation (new links may have rendered). A
    * no-op where `IntersectionObserver` is unavailable. */
  private def installViewportPrefetch(app: MeltKit): Unit =
    if js.typeOf(dom.window.asInstanceOf[js.Dynamic].IntersectionObserver) != "undefined" then
      val observer = new dom.IntersectionObserver(
        (entries, obs) =>
          entries.foreach { entry =>
            if entry.isIntersecting then
              entry.target match
                case a: dom.html.Anchor =>
                  obs.unobserve(a)
                  internalUrlOf(a).foreach(url => runPrefetch(app, url.pathname))
                case _ => ()
          },
        new dom.IntersectionObserverInit {}
      )
      def scan(): Unit =
        val links = dom.document.querySelectorAll("a[href]")
        var i     = 0
        while i < links.length do
          links(i) match
            case a: dom.html.Anchor if preloadMode(a).contains("viewport") => observer.observe(a)
            case _                                                         => ()
          i += 1
      scan()
      Router.currentPath.subscribe(_ => scan())

  // ── Route dispatch ───────────────────────────────────────────────────────

  private def dispatch[F[_]: AsyncRunner](
    app:       MeltKitPlatform[F, dom.Element],
    stack:     OutletStack,
    outletEl:  dom.Element,
    path:      String,
    hydrating: Boolean = false
  ): Unit =
    val segments = path.split("/").filter(_.nonEmpty).toList
    val matched  = app.routes.find { r =>
      r.method == "GET" && PathSegment.matches(r.segments, segments)
    }
    matched.foreach { route =>
      val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
      val factory   = new MeltContextFactory[F, dom.Element]:
        override def build[P <: AnyNamedTuple, B](
          params:  P,
          decoder: BodyDecoder[B]
        ): MeltContext[F, P, B, dom.Element] =
          BrowserMeltContext[F, P, B](params, decoder, stack, hydrating)
      route.tryHandle(rawValues, factory).foreach { thunk =>
        if hydrating then
          // Claim the SSR DOM in place: the render composes layout+page inside this
          // cursor (see BrowserMeltContext.render), reusing the existing nodes.
          Hydrating.withCursor(new HydrationCursor(outletEl.firstChild)) {
            summon[AsyncRunner[F]].runAndForget(thunk())
          }
          Hydrating.flush()
        else summon[AsyncRunner[F]].runAndForget(thunk())
      }
    }
