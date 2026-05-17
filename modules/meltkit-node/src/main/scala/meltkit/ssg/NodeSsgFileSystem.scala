/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.scalajs.js
import scala.scalajs.js.annotation.*

import meltkit.NodeFsStats

/** Scala.js facade for the Node.js `fs` module — SSG write operations.
  *
  * A separate facade object (alongside the existing [[meltkit.NodeFs]]) avoids
  * mixing read-only static-file-serving methods with build-time write methods.
  * Node.js caches modules, so multiple `@JSImport("fs", ...)` objects are safe.
  *
  * Requires Node.js v14.14.0+ for `rmSync` with `recursive` option.
  */
@js.native @JSImport("fs", JSImport.Namespace)
object NodeFsSsg extends js.Object:
  def readFileSync(path:  String, encoding: String):                   String           = js.native
  def writeFileSync(path: String, data:     String, encoding: String): Unit             = js.native
  def mkdirSync(path:     String, options:  js.Dynamic):               Unit             = js.native
  def rmSync(path:        String, options:  js.Dynamic):               Unit             = js.native
  def copyFileSync(src:   String, dst:      String):                   Unit             = js.native
  def readdirSync(path:   String):                                     js.Array[String] = js.native
  def existsSync(path:    String):                                     Boolean          = js.native
  def lstatSync(path:     String):                                     NodeFsStats      = js.native
  def mkdtempSync(prefix: String):                                     String           = js.native
