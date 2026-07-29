/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/** A [[StreamBody]] for `Future`-based server transports (Node.js and JVM/Undertow)
  * — the counterpart of the http4s adapter's `Fs2StreamBody`.
  *
  * Carries the shell `head` (already including the streaming swap bootstrap), one
  * `Future` per top-level `<melt:await>` boundary yielding `(chunk HTML, raw seeds)`,
  * and the document `tail`. [[FutureStreamBody.drive]] writes them to a
  * transport-specific sink; each binding supplies only how to write a String chunk
  * and how to close.
  *
  * @param head   the shell (fallbacks + swap bootstrap), flushed first
  * @param chunks per-boundary futures — resolved concurrently (eager), flushed as
  *               each settles (out-of-order) — each yielding the swap-chunk HTML and its seeds
  * @param tail   the document tail (deferred hydrate bootstrap + closing markup)
  */
final case class FutureStreamBody(
  head:   String,
  chunks: List[Future[(String, List[(String, String)])]],
  tail:   String
) extends StreamBody

object FutureStreamBody:

  /** Drives `body` against a sink: flush the head, then each chunk '''as it settles'''
    * (out-of-order — a slow boundary never delays a faster one; the client swaps by id
    * regardless of arrival order), then the merged `data-melt-queries` seed script +
    * tail once every boundary is done, then close.
    *
    * All writes are threaded through one serialised `chain` (guarded by `lock`) so the
    * sink is only ever touched one write at a time — required by transports like
    * Undertow whose sender rejects concurrent sends. `lock` is uncontended on Node.js
    * (single-threaded) and protects the shared state on the JVM. A chunk that fails is
    * skipped (its boundary keeps its server-rendered fallback); the stream still
    * completes with the tail + close (headers are already sent). */
  def drive(
    body:       FutureStreamBody,
    writeAsync: String => Future[Unit],
    close:      () => Unit
  )(using ExecutionContext): Unit =
    val lock      = new AnyRef
    val seeds     = scala.collection.mutable.ListBuffer.empty[(String, String)]
    var chain     = writeAsync(body.head) // serialised write chain (guarded by lock)
    var remaining = body.chunks.size

    def append(s: String): Unit = lock.synchronized { chain = chain.flatMap(_ => writeAsync(s)) }

    def finishIfDone(): Unit =
      val (done, snapshot) = lock.synchronized { remaining -= 1; (remaining == 0, seeds.toList) }
      if done then
        append(SsrRenderScope.streamSeedScript(snapshot) + body.tail)
        lock.synchronized(chain).onComplete(_ => close())

    if body.chunks.isEmpty then
      append(body.tail)
      lock.synchronized(chain).onComplete(_ => close())
    else
      body.chunks.foreach { chunkF =>
        chunkF.onComplete {
          case Success((html, s)) =>
            lock.synchronized { seeds ++= s }
            append(html)
            finishIfDone()
          case Failure(_) => finishIfDone()
        }
      }
