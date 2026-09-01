/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package model

/** A plain domain model — no database library required. In a real app this would come from
  * your data layer (e.g. an ldbc `Table[User]`), but the compile-time link checking works the
  * same either way: what matters is that `id` has a concrete type (`Long`) that the route's
  * `param[Long]` can be checked against.
  */
case class User(id: Long, name: String, email: String)
