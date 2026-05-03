/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A type-safe key for a request-scoped local value.
  *
  * Each call to [[LocalKey.make]] produces a distinct key.
  * Keys are typically defined as top-level or companion-object `val`s:
  *
  * {{{
  * val userKey:    LocalKey[AuthUser] = LocalKey.make[AuthUser]
  * val traceIdKey: LocalKey[String]   = LocalKey.make[String]
  * }}}
  */
final class LocalKey[A] private[meltkit] ()

object LocalKey:

  /** Creates a new unique key for values of type `A`. */
  def make[A]: LocalKey[A] = new LocalKey[A]()

/** A mutable, request-scoped store for arbitrary typed values.
  *
  * One [[Locals]] instance is created per matched request and shared between
  * all middleware (via [[RequestInfo.locals]]) and the route handler
  * (via [[ServerMeltContext.locals]]).
  *
  * Reading values is done directly; writing is done via adapter-provided
  * extension methods that return `F[Unit]` (e.g. `LocalsOps` in the http4s adapter):
  *
  * {{{
  * val userKey = LocalKey.make[AuthUser]
  *
  * // middleware — set via adapter extension method (returns F[Unit])
  * app.use { (info, next) =>
  *   verifyToken(info.header("Authorization")) match
  *     case None       => IO.pure(Unauthorized())
  *     case Some(user) =>
  *       info.locals.set(userKey, user) *> next
  * }
  *
  * // handler — get directly (synchronous read)
  * app.on(Endpoint.get("profile").response[Profile]) { ctx =>
  *   val user = ctx.locals.get(userKey).get  // set by middleware
  *   profileStore.find(user.id).map(ctx.ok(_))
  * }
  * }}}
  *
  * ==Thread safety==
  *
  * [[Locals]] uses `scala.collection.mutable.HashMap` internally, which is
  * safe for the typical middleware pattern: middleware sets values sequentially
  * before the handler reads them (guaranteed by the [[Defer]] dispatch).
  * On Scala.js the runtime is single-threaded so there is no contention.
  * Compound read-modify-write sequences are NOT atomic — if parallel fibers
  * within a single request need compound atomicity, use `cats.effect.Ref` separately.
  */
final class Locals:

  import scala.collection.mutable
  private val store = mutable.HashMap.empty[LocalKey[?], Any]

  /** Returns the value associated with `key`, or `None` if not set. */
  def get[A](key: LocalKey[A]): Option[A] =
    store.get(key).map(_.asInstanceOf[A])

  /** Returns `true` if `key` has an associated value. */
  def contains(key: LocalKey[?]): Boolean =
    store.contains(key)

  /** Associates `key` with `value`, overwriting any previous value.
    *
    * This is a synchronous side-effecting operation.
    * Wrap it in the effect type of your adapter when calling from middleware:
    *
    * {{{
    * // Cats Effect
    * app.use { (info, next) =>
    *   IO { info.locals.set(userKey, user) } *> next
    * }
    * }}}
    *
    * Alternatively, import `LocalsOps.*` from the http4s adapter for an
    * `F[Unit]`-returning extension method with explicit type parameter:
    * {{{
    * import meltkit.adapter.http4s.LocalsOps.*
    * info.locals.set[IO](userKey, user)   // returns IO[Unit]
    * }}}
    */
  def set[A](key: LocalKey[A], value: A): Unit =
    store(key) = value

  /** Removes the value associated with `key`. No-op if not present.
    *
    * Wrap in the effect type of your adapter when calling from middleware,
    * same as [[set]].
    */
  def remove[A](key: LocalKey[A]): Unit =
    store -= key

  // Package-private aliases used by LocalsOps to wrap in F[Unit].
  private[meltkit] def unsafeSet[A](key: LocalKey[A], value: A): Unit   = set(key, value)
  private[meltkit] def unsafeRemove[A](key: LocalKey[A]): Unit          = remove(key)
