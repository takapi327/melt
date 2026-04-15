/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

/** Platform-independent abstraction of a DOM Event. */
trait Event:
  def `type`:            String
  def preventDefault():  Unit
  def stopPropagation(): Unit
  def target:            Option[EventTarget]
  def currentTarget:     Option[EventTarget]
