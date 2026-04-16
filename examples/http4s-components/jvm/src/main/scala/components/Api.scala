/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import melt.runtime.Var

/** JVM no-op implementation of Api.
  *
  * SSR does not perform client-side fetches — these stubs exist for type
  * compatibility so that shared `.melt` components can reference Api methods.
  */
object Api:
  def fetchAll(todos:   Var[List[Todo]], users: Var[List[User]], loaded: Var[Boolean]): Unit = ()
  def addTodo(text:     String, todos:          Var[List[Todo]]):                       Unit = ()
  def toggleTodo(id:    String):                                                        Unit = ()
  def deleteTodo(id:    String):                                                        Unit = ()
  def fetchUsers(users: Var[List[User]]):                                               Unit = ()
