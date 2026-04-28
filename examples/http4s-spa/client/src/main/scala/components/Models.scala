/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

case class Todo(id: String, text: String, done: Boolean = false) derives io.circe.Codec
case class User(id: Int, name: String, email: String, role: String) derives io.circe.Codec
