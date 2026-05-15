/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** SvelteKit's `handle()` hook equivalent.
  *
  * Wraps around the request pipeline and can modify the request event,
  * transform the HTML response, or filter response headers.
  *
  * Register via `app.use()`:
  *
  * {{{
  * // Simple form — like SvelteKit's handle
  * app.use { (event, resolve) =>
  *   event.cookie("session") match
  *     case None    => Future.successful(Unauthorized())
  *     case Some(_) => resolve()
  * }
  *
  * // With ResolveOptions (HTML transformation)
  * app.use { (event, resolve) =>
  *   resolve(ResolveOptions(transformPageChunk = Some(html => html.replace("old", "new"))))
  * }
  * }}}
  *
  * Execution order: Request -> Hook chain (registration order) -> Route Handler
  */
trait ServerHook[F[_]]:
  def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response]

/** Resolves the request through the remaining pipeline. */
trait Resolve[F[_]]:
  def apply(): F[Response]
  def apply(options: ResolveOptions): F[Response]

/** Options for [[Resolve]] to transform the response. */
case class ResolveOptions(
  transformPageChunk:              Option[String => String]  = None,
  filterSerializedResponseHeaders: Option[String => Boolean] = None
)

/** A bundle of hooks registered via [[MeltApp.hooks]]. */
case class ServerHooks[F[_]](
  handle:      Seq[ServerHook[F]]       = Nil,
  handleError: Option[ErrorHandler[F]] = None
)

object ServerHooks:
  def empty[F[_]]: ServerHooks[F] = ServerHooks[F]()

object ServerHook:

  /** Composes multiple hooks into a single hook (right-fold). Safe for empty lists. */
  def sequence[F[_]](hooks: ServerHook[F]*): ServerHook[F] =
    hooks.toList match
      case Nil =>
        new ServerHook[F]:
          def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response] =
            resolve()
      case single :: Nil => single
      case multiple      =>
        multiple.reduceRight { (outer, inner) =>
          new ServerHook[F]:
            def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response] =
              outer.handle(
                event,
                new Resolve[F]:
                  def apply(): F[Response] = inner.handle(event, resolve)
                  def apply(opts: ResolveOptions): F[Response] =
                    inner.handle(
                      event,
                      new Resolve[F]:
                        def apply(): F[Response]                    = resolve(opts)
                        def apply(innerOpts: ResolveOptions): F[Response] = resolve(mergeOptions(opts, innerOpts))
                    )
              )
        }

  /** Creates a [[ServerHook]] from a simple function.
    *
    * The `resolve` callback is simplified to `() => F[Response]` for
    * hooks that don't need [[ResolveOptions]].
    */
  def apply[F[_]](fn: (RequestEvent[F], Resolve[F]) => F[Response]): ServerHook[F] =
    new ServerHook[F]:
      def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response] =
        fn(event, resolve)

  /** CSRF protection hook.
    *
    * Validates the `Origin` header on form requests to prevent CSRF attacks.
    *
    * Validation logic:
    *   1. `config.enabled = false` → skip
    *   2. Not a form Content-Type → skip (protected by CORS preflight)
    *   3. Safe method (`GET`/`HEAD`/`OPTIONS`/`TRACE`) → skip
    *   4. Path matches `exemptPaths` → skip
    *   5. `Origin` header is absent → reject
    *   6. `Origin` matches server origin → allow
    *   7. `Origin` is in `trustedOrigins` → allow
    *   8. Otherwise → 403 Forbidden
    *
    * {{{
    * app.use(ServerHook.csrf())
    * app.use(ServerHook.csrf(CsrfConfig(trustedOrigins = Set("https://app.example.com"))))
    * }}}
    */
  def csrf[F[_]: Pure](config: CsrfConfig = CsrfConfig.default): ServerHook[F] =
    new ServerHook[F]:
      def handle(event: RequestEvent[F], resolve: Resolve[F]): F[Response] =
        if !config.enabled then resolve()
        else if !isFormContentType(event) then resolve()
        else
          val method = event.method.toUpperCase
          if !Set("POST", "PUT", "PATCH", "DELETE").contains(method) then resolve()
          else if isExemptPath(event.requestPath, config.exemptPaths) then resolve()
          else
            event.header("Origin") match
              case None =>
                Pure[F].pure(Forbidden("CSRF check failed: missing Origin header"))
              case Some(origin) =>
                val serverOrigin = resolveServerOrigin(event, config.trustForwardedHost)
                if origin == serverOrigin || config.trustedOrigins.contains(origin) then resolve()
                else Pure[F].pure(Forbidden("CSRF check failed: Origin mismatch"))

  private def isFormContentType[F[_]](info: RequestEvent[F]): Boolean =
    info.header("Content-Type").exists { ct =>
      val base = ct.split(';').head.trim.toLowerCase
      base == "application/x-www-form-urlencoded" ||
      base == "multipart/form-data" ||
      base == "text/plain"
    }

  private def isExemptPath(requestPath: String, exemptPaths: List[String]): Boolean =
    exemptPaths.exists { exemptPath =>
      requestPath == exemptPath ||
      requestPath.startsWith(exemptPath.stripSuffix("/") + "/")
    }

  private def resolveServerOrigin[F[_]](info: RequestEvent[F], trustForwardedHost: Boolean): String =
    val host =
      if trustForwardedHost then info.header("X-Forwarded-Host").orElse(info.header("Host")).getOrElse("")
      else info.header("Host").getOrElse("")
    val proto = info
      .header("X-Forwarded-Proto")
      .orElse(if host.endsWith(":443") then Some("https") else None)
      .getOrElse("https")
    s"$proto://$host"

  private def mergeOptions(outer: ResolveOptions, inner: ResolveOptions): ResolveOptions =
    ResolveOptions(
      transformPageChunk = (outer.transformPageChunk, inner.transformPageChunk) match
        case (Some(f), Some(g)) => Some(f.andThen(g))
        case (a @ Some(_), _)   => a
        case (_, b @ Some(_))   => b
        case _                  => None,
      filterSerializedResponseHeaders =
        outer.filterSerializedResponseHeaders.orElse(inner.filterSerializedResponseHeaders)
    )
