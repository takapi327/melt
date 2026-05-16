/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** Scala.js facade for Node.js `http` module.
  *
  * Follows the same pattern as the `AsyncLocalStorage` facade in
  * [[Router.scala]].
  */
@js.native @JSImport("http", JSImport.Namespace)
object NodeHttp extends js.Object:
  def createServer(
    handler: js.Function2[IncomingMessage, ServerResponse, Unit]
  ): HttpServer = js.native

/** Facade for Node.js `http.IncomingMessage`. */
@js.native
trait IncomingMessage extends js.Object:
  val method:                                            String                = js.native
  val url:                                               String                = js.native
  val headers:                                           js.Dictionary[String] = js.native
  def on(event: String, cb: js.Function1[js.Any, Unit]): Unit                  = js.native

/** Facade for Node.js `http.ServerResponse`. */
@js.native
trait ServerResponse extends js.Object:
  def writeHead(status: Int, headers: js.Dictionary[String]): Unit = js.native
  def end(body:         String):                              Unit = js.native
  def end():                                                  Unit = js.native

/** Facade for Node.js `http.Server`. */
@js.native
trait HttpServer extends js.Object:
  def listen(port: Int, host: String, cb: js.Function0[Unit]): Unit = js.native
  def close(cb:    js.Function0[Unit]):                        Unit = js.native

/** Facade for Node.js `fs` module (subset used for static file serving). */
@js.native @JSImport("fs", JSImport.Namespace)
object NodeFs extends js.Object:
  def readFile(path:   String, cb: js.Function2[js.Error, js.typedarray.Uint8Array, Unit]): Unit        = js.native
  def existsSync(path: String):                                                             Boolean     = js.native
  def lstatSync(path:  String):                                                             NodeFsStats = js.native

/** Facade for Node.js `fs.Stats`. */
@js.native
trait NodeFsStats extends js.Object:
  def isFile():         Boolean = js.native
  def isSymbolicLink(): Boolean = js.native

/** Facade for Node.js `path` module. */
@js.native @JSImport("path", JSImport.Namespace)
object NodePath extends js.Object:
  def join(paths:    String*): String = js.native
  def resolve(paths: String*): String = js.native
  def extname(p:     String):  String = js.native
  def normalize(p:   String):  String = js.native
