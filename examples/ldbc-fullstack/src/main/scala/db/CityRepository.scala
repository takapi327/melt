/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package db

import cats.effect.IO

import ldbc.Connector
import ldbc.dsl.*
import ldbc.schema.TableQuery

import models.City

/** Link #2: queries built from the schema.
  *
  * Nothing here restates the row shape — `selectAll` carries `City` out of
  * [[CityTable]], so the result type is decided by the schema, not by this file.
  */
class CityRepository(connector: Connector[IO]):

  private val cities = TableQuery[CityTable]

  def findAll(limit: Int): IO[List[City]] =
    cities.selectAll.limit(limit).query.to[List].readOnly(connector)

  def findById(id: Int): IO[Option[City]] =
    cities.selectAll.where(_.id === id).query.to[Option].readOnly(connector)
