/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import melt.runtime.json.{ PropsCodec, SimpleJson }
import melt.runtime.{ Async, Signal, State }

/** The reactive result of a [[QueryFn]] invocation.
  *
  * Holds a [[melt.runtime.State]] of [[melt.runtime.Async]] so the same value
  * works on both platforms: on the JVM (SSR) the state is frozen at its initial
  * snapshot (`Loading`, or a seeded `Done`), while on the client it is a live
  * reactive cell that a template re-renders as the query settles.
  *
  * Instances are produced by the platform-specific `apply` extension on
  * [[QueryFn]] (a no-op fetch on the JVM, a real request on the client), never
  * constructed directly.
  *
  * {{{
  * val posts = list()          // : Query[List[Post]]
  * {posts.state.value match
  *   case Async.Loading    => <p>Loading…</p>
  *   case Async.Failed(e)  => <p class="error">{e.getMessage}</p>
  *   case Async.Done(list) => <ul>{list.map(p => <li>{p.title}</li>)}</ul>
  * }
  * <button onclick={_ => posts.refresh()}>Reload</button>
  * }}}
  */
final class Query[Out] private[meltkit] (
  private[meltkit] val name:     String,
  private[meltkit] val argsJson: String,
  private[meltkit] val outCodec: PropsCodec[Out],
  initial:                       Async[Out],
  private val runFetch:          Query[Out] => Unit
):

  /** Stable identity `name + args` — matches a single-flight update entry to the
    * query it should refresh, and keys client-side request coalescing. */
  private[meltkit] def key: String = s"$name\n$argsJson"

  private val _state = State[Async[Out]](initial)

  /** Applies a single-flight update: decodes the piggybacked JSON with this
    * query's own codec and resolves the state (client-only; a no-op on the JVM).
    * Encapsulated here so callers need not know the existential `Out`. */
  private[meltkit] def applyUpdate(json: SimpleJson.JsonValue): Unit =
    _state.set(Async.Done(outCodec.decode(json)))

  /** Optimistically transforms the current resolved value (no effect while
    * `Loading`/`Failed`) and returns a thunk that restores the previous state —
    * used by a single-flight mutation to show an expected result immediately and
    * roll back if the request fails. */
  private[meltkit] def applyOptimistic(f: Out => Out): () => Unit =
    val prev = _state.value
    prev match
      case Async.Done(v) => _state.set(Async.Done(f(v)))
      case _             => ()
    () => _state.set(prev)

  /** The reactive query state, rendered by matching on [[melt.runtime.Async]]. */
  val state: Signal[Async[Out]] = _state.signal

  /** Re-runs the query. On the JVM (SSR) this is a no-op; on the client it moves
    * the state to `Loading` and issues a fresh request. */
  def refresh(): Unit = runFetch(this)

  /** Overwrites the state with a resolved value — used for optimistic updates and
    * single-flight mutation responses (see design §8). */
  def set(value: Out): Unit = _state.set(Async.Done(value))

  // ── Runtime-internal transitions, driven by the client fetch ────────────────
  private[meltkit] def setLoading(): Unit         = _state.set(Async.Loading)
  private[meltkit] def setDone(value: Out): Unit  = _state.set(Async.Done(value))
  private[meltkit] def setFailed(e: Throwable): Unit = _state.set(Async.Failed(e))
