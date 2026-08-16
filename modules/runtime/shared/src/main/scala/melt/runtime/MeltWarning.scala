/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** What kind of thing the runtime refused to emit.
  *
  * Carried by [[MeltWarning]] so that a handler can triage without matching on
  * message text: a security block (`BlockedUrl` / `BlockedCssValue`) usually
  * deserves an alert, while a dropped spread attribute is a developer-facing
  * hint. Message wording is free to change; these cases are the stable contract.
  */
enum MeltWarningKind:

  /** A URL attribute value used a blocked protocol (`javascript:`, `data:text/html`, …). */
  case BlockedUrl

  /** A CSS property value contained a blocked construct (`expression(`, `@import`, …). */
  case BlockedCssValue

  /** A spread attribute was dropped because its name is not a valid HTML attribute name. */
  case DroppedAttribute

  /** A spread attribute was dropped because it looked like an event handler (`on*`). */
  case DroppedEventHandler

  /** A spread attribute was dropped because its value is a function. */
  case DroppedFunctionAttr

  /** A spread attribute was dropped because its value is a Tuple / Named Tuple. */
  case DroppedTupleAttr

/** A single runtime warning.
  *
  * Deliberately carries no request context. The sink is supplied by whoever owns
  * the render (see `ServerRenderer.Config.warningSink`), and that layer already
  * knows which request it is serving — embedding a context here would duplicate
  * it and force `melt-runtime` to model concepts that live in `meltkit`.
  *
  * @param kind    stable classification, safe to match on
  * @param message human-readable text, wording not part of the contract
  * @param attr    attribute name the warning is about, when there is one
  * @param value   the offending value, truncated, when it is safe to surface
  */
final case class MeltWarning(
  kind:    MeltWarningKind,
  message: String,
  attr:    Option[String] = None,
  value:   Option[String] = None
)
