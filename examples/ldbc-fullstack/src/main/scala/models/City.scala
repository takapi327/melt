/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package models

/** The row type shared by the whole chain.
  *
  * `CityTable.*` maps the DB columns onto this via `.to[City]`, the MeltKit
  * handlers pass it around, and the `.melt` templates read its fields. It is the
  * single value that the DB schema, the endpoint and the template all agree on —
  * change a field here and every one of those three fails to compile.
  */
case class City(
  id:          Int,
  name:        String,
  countryCode: String,
  district:    String,
  population:  Int
)
