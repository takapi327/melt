/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

/** Platform-independent abstraction of a DOM KeyboardEvent. */
trait KeyboardEvent extends Event:
  def key: String
  def code: String
  def shiftKey: Boolean
  def ctrlKey: Boolean
  def altKey: Boolean
  def metaKey: Boolean
  def repeat: Boolean
