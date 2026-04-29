/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.NamedTuple.AnyNamedTuple

import org.scalajs.dom

import melt.runtime.Mount

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
  *     val app    = MeltRouter()
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
  def mount[F[_]: EffectRunner](app: MeltKitPlatform[F, dom.Element], rootEl: dom.Element): Unit =
    ensureLinkInterceptor()
    dispatch(app, rootEl, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, rootEl, path) }

  /** Renders `shell` once into `rootEl`, then routes future navigations into
    * the `[data-melt-outlet]` element found within the shell.
    *
    * @param app    the [[MeltKit]] router whose routes will handle URL changes
    * @param rootEl the DOM element to mount the shell into
    * @param shell  the persistent shell component (e.g. `Layout()`)
    */
  def mountWithShell[F[_]: EffectRunner](
    app:    MeltKitPlatform[F, dom.Element],
    rootEl: dom.Element,
    shell:  dom.Element
  ): Unit =
    ensureLinkInterceptor()
    rootEl.innerHTML = ""
    Mount(rootEl, shell)
    val outlet = Option(rootEl.querySelector("[data-melt-outlet]")).getOrElse(rootEl)
    dispatch(app, outlet, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, outlet, path) }

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
      findAnchor(event).foreach { anchor =>
        val href = anchor.getAttribute("href")
        if href != null && href.nonEmpty then
          val hasExternal = Option(anchor.getAttribute("rel"))
            .exists(_.split("\\s+").contains("external"))
          val hasTarget   = Option(anchor.getAttribute("target")).exists(_.nonEmpty)
          val hasDownload = anchor.hasAttribute("download")
          if !hasExternal && !hasTarget && !hasDownload then
            try
              val url = new dom.URL(href, dom.window.location.href)
              if (url.protocol == "https:" || url.protocol == "http:")
                && url.origin == dom.window.location.origin
              then
                event.preventDefault()
                val path = url.pathname + (if url.search.nonEmpty then url.search else "")
                Router.navigate(path)
            catch case _: Throwable => () // Invalid URL — let browser handle it
      }

  private def findAnchor(event: dom.MouseEvent): Option[dom.html.Anchor] =
    @annotation.tailrec
    def loop(node: dom.Node | Null): Option[dom.html.Anchor] =
      if node == null then None
      else
        node match
          case a: dom.html.Anchor if a.hasAttribute("href") => Some(a)
          case _                                            => loop(node.parentNode)
    event.target match
      case n: dom.Node => loop(n)
      case _           => None

  // ── Route dispatch ───────────────────────────────────────────────────────

  private def dispatch[F[_]: EffectRunner](
    app:      MeltKitPlatform[F, dom.Element],
    outletEl: dom.Element,
    path:     String
  ): Unit =
    val segments = path.split("/").filter(_.nonEmpty).toList
    val matched  = app.routes.find { r =>
      r.method == "GET" && PathSegment.matches(r.segments, segments)
    }
    matched.foreach { route =>
      val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
      val factory   = new MeltContextFactory[F, dom.Element]:
        def build[P <: AnyNamedTuple, B](params: P, decoder: BodyDecoder[B]): MeltContext[F, P, B, dom.Element] =
          BrowserMeltContext[F, P, B](params, decoder, outletEl)
        def buildServer[P <: AnyNamedTuple, B](
          params:  P,
          decoder: BodyDecoder[B]
        ): Option[ServerMeltContext[F, P, B, dom.Element]] =
          None
      route.tryHandle(rawValues, factory).foreach(summon[EffectRunner[F]].runAndForget)
    }
