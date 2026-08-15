/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package server

import cats.effect.*

import com.comcast.ip4s.*

import ldbc.connector.*

import org.http4s.ember.server.EmberServerBuilder

import meltkit.*
import meltkit.adapter.http4s.Http4sAdapter
import meltkit.adapter.http4s.Http4sAdapter.given

import components.*
import db.CityRepository

/** ldbc × Melt × MeltKit (http4s) — one scalac type check from schema to template.
  *
  * The chain this example exists to demonstrate:
  *
  *   `CityTable` columns → `.to[City]` → `CityRepository` result type
  *     → handler return type → `Props` → the `{…}` expressions in the template
  *
  * Every arrow is an ordinary Scala type relation, so breaking any link fails the
  * build rather than the request.
  *
  * {{{
  *   docker compose up -d           # MySQL with the `world` sample database
  *   sbt "ldbc-fullstack/run"       # http://localhost:9095
  * }}}
  */
object Server extends IOApp.Simple:

  private val cityId = param[Int]("id")

  private val dataSource = MySQLDataSource
    .build[IO]("127.0.0.1", 13306, "ldbc")
    .setPassword("password")
    .setDatabase("world")

  private val connector = Connector.fromDataSource(dataSource)
  private val repo      = CityRepository(connector)

  private def buildApp(): MeltKit[IO] =
    val app = MeltKit[IO]()

    // Link #3: the handler's return type is fixed by the repository, which is
    // fixed by the schema. Nothing restates the row shape.
    app.get("") { ctx =>
      repo.findAll(50).map(cities => ctx.render(CityListPage(CityListPage.Props(cities = cities))))
    }

    // Link #4: `ctx.params.id` is `Int` because `cityId` is `param[Int]`.
    app.get("cities" / cityId) { ctx =>
      repo.findById(ctx.params.id).map {
        case Some(city) => ctx.render(CityDetailPage(CityDetailPage.Props(city = city)))
        case None       => ctx.notFound(s"City ${ ctx.params.id } not found")
      }
    }

    app

  def run: IO[Unit] =
    for
      routes <- Http4sAdapter.ssrRoutes(
                  buildApp(),
                  fs2.io.file.Path("dist"),
                  ViteManifest.fromEntries(Map.empty)
                )
      _ <- EmberServerBuilder
             .default[IO]
             .withHost(host"0.0.0.0")
             .withPort(port"9095")
             .withHttpApp(routes.orNotFound)
             .build
             .useForever
    yield ()
