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
  *     val app    = MeltKit[Id]()
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
  * Use [[Router.navigate]] in link handlers to trigger client-side
  * navigation without a full page reload:
  *
  * {{{
  * <a onclick={_ => Router.navigate("/users/42")}>Alice</a>
  * }}}
  */
object BrowserAdapter:

  /** Mounts the [[MeltKit]] router to the browser, replacing the full `rootEl`
    * content on each navigation.
    *
    * Dispatches the matching route for the current URL immediately, then listens
    * for `popstate` events — fired by [[Router.navigate]], browser back /
    * forward buttons — to re-dispatch on each navigation.
    *
    * @param app    the [[MeltKit]] router whose routes will handle URL changes
    * @param rootEl the DOM element used as the mount target for components
    */
  def mount[F[_]: EffectRunner](app: MeltKit[F], rootEl: dom.Element): Unit =
    dispatch(app, rootEl, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, rootEl, path) }

  /** Renders `shell` once into `rootEl`, then routes future navigations into
    * the `[data-melt-outlet]` element found within the shell.
    *
    * The shell (layout, navigation bar, sidebar, …) is created once and
    * stays in the DOM across navigations. Only the outlet's content is
    * replaced when the URL changes, avoiding unnecessary re-creation of
    * persistent UI elements and preserving their state.
    *
    * If no `[data-melt-outlet]` element is present inside the rendered
    * shell, `rootEl` itself is used as the outlet (same behaviour as
    * [[mount]]).
    *
    * @param app    the [[MeltKit]] router whose routes will handle URL changes
    * @param rootEl the DOM element to mount the shell into
    * @param shell  the persistent shell component (e.g. `Layout()`)
    */
  def mountWithShell[F[_]: EffectRunner](
    app:    MeltKit[F],
    rootEl: dom.Element,
    shell:  dom.Element
  ): Unit =
    rootEl.innerHTML = ""
    Mount(rootEl, shell)
    val outlet = Option(rootEl.querySelector("[data-melt-outlet]")).getOrElse(rootEl)
    dispatch(app, outlet, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, outlet, path) }

  private def dispatch[F[_]: EffectRunner](app: MeltKit[F], outletEl: dom.Element, path: String): Unit =
    val segments = path.split("/").filter(_.nonEmpty).toList
    val matched  = app.routes.find { r =>
      r.method == "GET" && PathSegment.matches(r.segments, segments)
    }
    matched.foreach { route =>
      val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
      val factory   = new MeltContextFactory[F]:
        def build[P <: AnyNamedTuple, B](params: P, decoder: BodyDecoder[B]): MeltContext[F, P, B] =
          BrowserMeltContext[F, P, B](params, decoder, outletEl)
        def buildServer[P <: AnyNamedTuple, B](params: P, decoder: BodyDecoder[B]): Option[ServerMeltContext[F, P, B]] =
          None
      route.tryHandle(rawValues, factory).foreach(summon[EffectRunner[F]].runAndForget)
    }
