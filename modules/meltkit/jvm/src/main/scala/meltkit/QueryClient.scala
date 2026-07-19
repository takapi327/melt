/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.Async

/** Server-side (SSR) invocation of a [[QueryFn]].
  *
  * Returns a [[Query]] frozen at `Async.Loading`: SSR renders only the initial
  * snapshot and issues no request, so `refresh` is a no-op here. The client
  * counterpart (in `meltkit/js`) performs the real fetch after hydration.
  *
  * Kept as a platform split (JVM no-op / JS fetch) rather than in a JS-only
  * adapter because a query call appears in the component script and template —
  * which are compiled and rendered on the JVM during SSR — unlike a command,
  * which only ever runs inside an event handler (stripped from SSR output).
  */
extension [In, Out](fn: QueryFn[In, Out])
  def apply(in: In): Query[Out] =
    new Query[Out](fn.name, fn.inCodec.encodeToString(in), fn.outCodec, Async.Loading, _ => ())
  def apply()(using ev: Unit =:= In): Query[Out] = fn(ev(()))

  /** Seeds the query with a value already available during SSR (a page-loader
    * prop), so the server renders the resolved data — matching the client, which
    * hydrates from the same prop. No request is made on either side. */
  def seeded(in: In, seed: Out): Query[Out] =
    new Query[Out](fn.name, fn.inCodec.encodeToString(in), fn.outCodec, Async.Done(seed), _ => ())
  def seeded(seed: Out)(using ev: Unit =:= In): Query[Out] = fn.seeded(ev(()), seed)
