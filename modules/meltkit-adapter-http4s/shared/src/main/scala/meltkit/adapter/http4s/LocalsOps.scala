/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s

import cats.effect.Sync
import meltkit.LocalKey
import meltkit.Locals

/** Extension methods that expose [[Locals]] write operations as `F[Unit]`.
  *
  * Importing this object brings `set` and `remove` into scope on any [[Locals]] value:
  *
  * {{{
  * import meltkit.adapter.http4s.LocalsOps.*
  *
  * app.use { (info, next) =>
  *   info.locals.set(userKey, user) *> next   // F[Unit]
  * }
  * }}}
  *
  * Write operations are deliberately absent from the core [[Locals]] class to prevent
  * accidental synchronous mutation outside an effect context.
  */
object LocalsOps:

  extension (locals: Locals)

    /** Wraps [[Locals.unsafeSet]] in `F`, suspending the mutation.
      *
      * `F` is inferred from the surrounding effect context (e.g. `IO`).
      */
    def set[F[_]: Sync, A](key: LocalKey[A], value: A): F[Unit] =
      Sync[F].delay(locals.unsafeSet(key, value))

    /** Wraps [[Locals.unsafeRemove]] in `F`, suspending the mutation. */
    def remove[F[_]: Sync, A](key: LocalKey[A]): F[Unit] =
      Sync[F].delay(locals.unsafeRemove(key))
