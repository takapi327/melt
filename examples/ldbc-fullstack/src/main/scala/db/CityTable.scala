/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package db

import ldbc.schema.*
import ldbc.statement.formatter.Naming

import models.City

/** ldbc schema definition for the MySQL `world`.`city` table.
  *
  * This is link #1 of the chain: the column types declared here are what
  * `.to[City]` has to line up with. Widening `population` to `Column[String]`
  * makes this file stop compiling before anything downstream is even consulted.
  */
class CityTable extends Table[City]("city"):

  given Naming = Naming.PASCAL

  def id:          Column[Int]    = int("ID").unsigned.autoIncrement.primaryKey
  def name:        Column[String] = char(35)
  def countryCode: Column[String] = char(3)
  def district:    Column[String] = char(20)
  def population:  Column[Int]    = int()

  override def * : Column[City] =
    (id *: name *: countryCode *: district *: population).to[City]
