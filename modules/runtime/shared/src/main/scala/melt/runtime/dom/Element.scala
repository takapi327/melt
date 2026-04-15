/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

/** Platform-independent abstraction of a DOM Element. */
trait Element extends Node with EventTarget:
  def tagName: String
  def getAttribute(name: String): Option[String]
  def setAttribute(name: String, value: String): Unit
  def removeAttribute(name: String): Unit
