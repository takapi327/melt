/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package routes

import meltkit.*

/** The single source of truth (SSOT) for this app's routes.
  *
  * Each [[meltkit.TypedRoute]] carries its full path — static segments and typed params — in
  * its type. The same value drives BOTH ends:
  *   - runtime routing on the server: `app.get(Routes.user) { ctx => ctx.params.id }`
  *   - compile-time link checking in templates: `href="/users/{u.id}"` is verified against it.
  *
  * Change a route here and every mismatched link becomes a compile error.
  */
object Routes:
  // No type annotation: the inferred `TypedRoute[…, ("users", PathParam["id", Long])]` is what
  // the link-checker reflects on. Widening to `TypedRoute[?, ?]` would erase the path type.
  val list = TypedRoute.root / "users"
  val user = TypedRoute.root / "users" / param[Long]("id")
