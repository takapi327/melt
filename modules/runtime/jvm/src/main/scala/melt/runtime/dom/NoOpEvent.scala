/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.dom

/** JVM no-op implementations of all event types.
  *
  * SSR removes event handlers, so these are never instantiated at runtime.
  * They exist only for type compatibility in shared code.
  */
class NoOpEvent extends Event:
  def `type`:            String              = ""
  def preventDefault():  Unit                = ()
  def stopPropagation(): Unit                = ()
  def target:            Option[EventTarget] = None
  def currentTarget:     Option[EventTarget] = None

class NoOpMouseEvent extends NoOpEvent with MouseEvent:
  def clientX:  Double  = 0.0
  def clientY:  Double  = 0.0
  def button:   Int     = 0
  def shiftKey: Boolean = false
  def ctrlKey:  Boolean = false
  def altKey:   Boolean = false
  def metaKey:  Boolean = false

class NoOpKeyboardEvent extends NoOpEvent with KeyboardEvent:
  def key:      String  = ""
  def code:     String  = ""
  def shiftKey: Boolean = false
  def ctrlKey:  Boolean = false
  def altKey:   Boolean = false
  def metaKey:  Boolean = false
  def repeat:   Boolean = false

class NoOpInputEvent extends NoOpEvent with InputEvent:
  def data:      Option[String] = None
  def inputType: String         = ""

class NoOpFocusEvent extends NoOpEvent with FocusEvent:
  def relatedTarget: Option[EventTarget] = None

class NoOpSubmitEvent extends NoOpEvent with SubmitEvent
