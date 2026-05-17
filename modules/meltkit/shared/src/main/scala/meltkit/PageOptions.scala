/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** Per-route rendering options.
  *
  * Passed to [[MeltApp.page]] to control how the page is rendered:
  *
  * {{{
  * app.page("dashboard")(handler, PageOptions(ssr = true, csr = true))
  * app.page("static-page")(handler, PageOptions(prerender = PrerenderOption.On))
  *
  * // Dynamic route: enumerate concrete paths via entries
  * app.page("posts" / postId)(
  *   ctx => Future.successful(ctx.render(PostPage(ctx.params.postId))),
  *   PageOptions(prerender = PrerenderOption.On, entries = List("/posts/hello", "/posts/world"))
  * )
  * }}}
  *
  * @param ssr           whether to server-side render the page (default `true`)
  * @param csr           whether to hydrate/client-render the page (default `true`)
  * @param prerender     whether to prerender the page at build time (default `Off`)
  * @param trailingSlash how to handle trailing slashes (default `Never`)
  * @param entries       concrete URL paths to generate for dynamic routes.
  *                      Required when the route contains path parameters and
  *                      `prerender` is `On` or `Auto`. Ignored for static routes.
  */
case class PageOptions(
  ssr:           Boolean         = true,
  csr:           Boolean         = true,
  prerender:     PrerenderOption = PrerenderOption.Off,
  trailingSlash: TrailingSlash   = TrailingSlash.Never,
  entries:       List[String]    = Nil
)

/** Controls whether a page is prerendered at build time. */
enum PrerenderOption:
  case Off
  case On
  case Auto

/** Controls trailing-slash behaviour for a route. */
enum TrailingSlash:
  case Never
  case Always
  case Ignore
