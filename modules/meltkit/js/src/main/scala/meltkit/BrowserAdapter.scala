/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import org.scalajs.dom

import scala.NamedTuple.AnyNamedTuple

import meltkit.codec.BodyDecoder

/** JS-side adapter that connects [[MeltKit]] route definitions to the browser's
  * History API.
  *
  * Mirrors [[meltkit.adapter.http4s.Http4sAdapter]] on the server side:
  * where the http4s adapter handles incoming HTTP requests, `BrowserAdapter`
  * handles URL changes in the browser, dispatching through the same [[MeltKit]]
  * route table.
  *
  * ==Usage==
  *
  * {{{
  * import org.scalajs.dom
  *
  * type Id = [A] =>> A
  * given EffectRunner[Id] with
  *   def runAndForget(fa: Response): Unit = ()
  *
  * object Main:
  *   def main(args: Array[String]): Unit =
  *     val rootEl = dom.document.getElementById("app")
  *     BrowserAdapter.mount(buildApp(), rootEl)
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

  /** Mounts the [[MeltKit]] router to the browser.
    *
    * Dispatches the matching route for the current URL immediately, then listens
    * for `popstate` events — fired by [[Router.navigate]], browser back /
    * forward buttons — to re-dispatch on each navigation.
    *
    * @param app    the [[MeltKit]] router whose routes will handle URL changes
    * @param rootEl the DOM element used as a mount target for components
    *               (passed to [[BrowserMeltContext]])
    */
  def mount[F[_]: EffectRunner](app: MeltKit[F], rootEl: dom.Element): Unit =
    dispatch(app, rootEl, Router.currentPath.value)
    Router.currentPath.subscribe { path => dispatch(app, rootEl, path) }

  private def dispatch[F[_]: EffectRunner](app: MeltKit[F], rootEl: dom.Element, path: String): Unit =
    val segments = path.split("/").filter(_.nonEmpty).toList
    val matched  = app.routes.find { r =>
      r.method == "GET" && PathSegment.matches(r.segments, segments)
    }
    matched.foreach { route =>
      val rawValues = route.segments.zip(segments).collect { case (PathSegment.Param(_), v) => v }
      val factory   = new MeltContextFactory[F]:
        def build[P <: AnyNamedTuple, B](params: P, decoder: BodyDecoder[B]): MeltContext[F, P, B] =
          BrowserMeltContext[F, P, B](params, decoder, rootEl)
      route.tryHandle(rawValues, factory).foreach(summon[EffectRunner[F]].runAndForget)
    }
