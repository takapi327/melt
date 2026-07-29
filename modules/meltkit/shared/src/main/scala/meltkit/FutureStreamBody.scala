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
  * @param chunks per-boundary futures — resolved concurrently (eager), flushed in
  *               registration order — each yielding the swap-chunk HTML and its seeds
  * @param tail   the document tail (deferred hydrate bootstrap + closing markup)
  */
final case class FutureStreamBody(
  head:   String,
  chunks: List[Future[(String, List[(String, String)])]],
  tail:   String
) extends StreamBody

object FutureStreamBody:

  /** Drives `body` against a sink: flush the head, then each chunk in registration
    * order (awaiting each `writeAsync` before the next — which also serialises
    * transports like Undertow whose sender rejects concurrent sends), then the
    * merged `data-melt-queries` seed script + tail, then close. Failures short-
    * circuit to the tail + close (best effort; headers are already sent). */
  def drive(
    body:       FutureStreamBody,
    writeAsync: String => Future[Unit],
    close:      () => Unit
  )(using ExecutionContext): Unit =
    val written = body.chunks.foldLeft(writeAsync(body.head).map(_ => List.empty[(String, String)])) {
      (accF, chunkF) =>
        accF.flatMap(acc => chunkF.flatMap { case (html, seeds) => writeAsync(html).map(_ => acc ++ seeds) })
    }
    written
      .flatMap(seeds => writeAsync(SsrRenderScope.streamSeedScript(seeds) + body.tail))
      .onComplete {
        case Success(_) => close()
        case Failure(_) => close()
      }
