/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s

import cats.effect.Async
import cats.syntax.all.*
import meltkit.ServerMeltKitPlatform
import org.http4s.*
import org.typelevel.ci.*

/** The outcome of a [[FormProbe]] request: the raw status, body and (lower-cased)
  * headers, so a test can assert without http4s types.
  */
final case class ProbeResponse(status: Int, body: String, headers: Map[String, String]):
  def location:               Option[String] = headers.get("location")
  def isOk:                   Boolean        = status >= 200 && status < 300
  def isRedirect:             Boolean        = status >= 300 && status < 400
  def contains(text: String): Boolean        = body.contains(text)

/** Drives a [[meltkit.MeltKit]] app's routes (and form actions) in memory,
  * without binding a server — the server-side counterpart of the client
  * `melt-testkit`. Reuses [[Http4sAdapter.routes]] so real query parsing, hooks
  * (e.g. CSRF) and action dispatch all run.
  *
  * {{{
  * val probe = FormProbe(app)
  * val r = probe.submit("login", fields = Map("email" -> "a@b.com", "password" -> "secret"),
  *                      origin = Some("http://localhost:3000"))
  * assertEquals(r.status, 303)
  * assertEquals(r.location, Some("/dashboard"))
  *
  * val e = probe.submit("posts", action = "publish", fields = Map("title" -> ""), enhance = true)
  * assert(e.contains("\"type\":\"failure\""))
  * }}}
  */
final class FormProbe[F[_]: Async: meltkit.Defer](app: ServerMeltKitPlatform[F]):

  private val routes = Http4sAdapter.routes(app)

  /** GET `path`. */
  def get(path: String): F[ProbeResponse] =
    run(Request[F](Method.GET, uri(path)))

  /** POST a urlencoded form to `path`.
    *
    * @param action  non-empty targets a named action via the `?/action` query
    * @param fields  the form fields (url-encoded via http4s `UrlForm`)
    * @param enhance sets the `x-melt-enhance` header (server replies with the JSON envelope)
    * @param origin  sets the `Origin` header (for the CSRF hook)
    * @param host    sets the `Host` header; defaults to the origin's host. Pass a
    *                different value than `origin` to emulate a cross-site attack.
    */
  def submit(
    path:    String,
    action:  String = "",
    fields:  Map[String, String] = Map.empty,
    enhance: Boolean = false,
    origin:  Option[String] = None,
    host:    Option[String] = None
  ): F[ProbeResponse] =
    val query   = if action.isEmpty then "" else s"?/$action"
    val request = Request[F](Method.POST, uri(path + query)).withEntity(UrlForm(fields.toSeq*))

    val enhanced =
      if enhance then request.putHeaders(Header.Raw(ci"x-melt-enhance", "true")) else request

    val resolvedHost = host.orElse(origin.flatMap(o => Uri.unsafeFromString(o).authority.map(_.renderString)))
    val originated   = origin.fold(enhanced)(o => enhanced.putHeaders(Header.Raw(ci"origin", o)))
    val hosted       = resolvedHost.fold(originated)(h => originated.putHeaders(Header.Raw(ci"host", h)))

    run(hosted)

  private def uri(path: String): Uri =
    Uri.unsafeFromString(if path.startsWith("/") then path else "/" + path)

  private def run(req: Request[F]): F[ProbeResponse] =
    routes.run(req).value.flatMap {
      case Some(resp) =>
        resp.as[String].map { body =>
          val hs = resp.headers.headers.map(h => h.name.toString.toLowerCase -> h.value).toMap
          ProbeResponse(resp.status.code, body, hs)
        }
      case None => Async[F].pure(ProbeResponse(404, "", Map.empty))
    }

object FormProbe:
  def apply[F[_]: Async: meltkit.Defer](app: ServerMeltKitPlatform[F]): FormProbe[F] =
    new FormProbe(app)
