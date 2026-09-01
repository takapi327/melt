/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.ziohttp

import zio.stream.ZStream

import meltkit.StreamBody

/** Carries the byte stream of a streaming-SSR response for the zio-http adapter.
  *
  * `meltkit` core keeps [[meltkit.StreamBody]] as an empty marker so it stays free of any
  * streaming library; each adapter supplies its own carrier (the http4s adapter has
  * `Fs2StreamBody`). Only this adapter constructs and reads a `ZStreamBody`.
  *
  * The stream is environment-free: `Body.fromStreamChunked` only accepts
  * `ZStream[Any, Throwable, Byte]`, so whoever builds the streaming response eliminates `R`
  * first (it has the environment in scope at that point, via `ZIO.environment` +
  * `provideEnvironment`). Keeping `R` here would push an un-dischargeable requirement into the
  * response conversion, which has no environment to give.
  */
final case class ZStreamBody(stream: ZStream[Any, Throwable, Byte]) extends StreamBody
