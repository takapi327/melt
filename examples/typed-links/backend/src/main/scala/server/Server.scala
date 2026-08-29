/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.implicitConversions

import components.UserListPage
import generated.AssetManifest
import meltkit.*
import model.User
import routes.Routes

/** SSR + hydration entry point for the type-safe-links example.
  *
  * The point of interest is that `Routes.user` is used in TWO places that the compiler keeps in
  * sync:
  *   - here, as the server route `app.get(Routes.user)` (with `ctx.params.id: Long`), and
  *   - in `UserListPage.melt`, as the link `href="/users/{u.id}"`.
  *
  * `meltLinkCheckingRoutes := Some("routes.Routes")` turns link checking on, so the template
  * link is validated against `Routes` at compile time. Rename the route or break the link and
  * the build fails — the "broken link = compile error" guarantee, end to end.
  */
@main def serve(): Unit =
  val app = MeltKit[Future]()

  val users = List(
    User(1, "Alice", "alice@example.com"),
    User(2, "Bob", "bob@example.com"),
    User(3, "Charlie", "charlie@example.com")
  )

  // Same `Routes.list` value converts to the runtime PathSpec for routing.
  app.get(Routes.list) { ctx =>
    Future.successful(ctx.render(UserListPage(UserListPage.Props(users = users))))
  }

  // `ctx.params.id` is typed `Long` because `Routes.user` ends in `param[Long]("id")` —
  // the exact type the template link is checked against.
  app.get(Routes.user) { ctx =>
    val body = users.find(_.id == ctx.params.id) match
      case Some(u) => s"${ u.name } <${ u.email }>"
      case None    => s"User ${ ctx.params.id } not found"
    Future.successful(ctx.text(body))
  }

  val template = scala.io.Source.fromResource("index.html").mkString

  UndertowServer
    .builder(app)
    .withPort(9096)
    .withTemplate(template)
    .withManifest(AssetManifest.manifest)
    .withClientDistDir(AssetManifest.clientDistDir)
    .start()
    .foreach(server => println(s"typed-links running on http://localhost:${ server.port }"))

  Thread.currentThread().join()
