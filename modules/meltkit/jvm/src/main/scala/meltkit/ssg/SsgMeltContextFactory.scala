/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.{ MeltContext, MeltContextFactory, ViteManifest }
import meltkit.codec.BodyDecoder

/** [[MeltContextFactory]] implementation for SSG.
  *
  * Passed to [[meltkit.Route.tryHandle]] in place of the http4s / node factory.
  * After the handler runs, [[lastContext]] exposes the [[SsgMeltContext]]
  * so that [[SsgGenerator]] can read [[SsgMeltContext.capturedHtml]].
  */
final class SsgMeltContextFactory[F[_]](
  requestPath: String,
  config:      SsgConfig
) extends MeltContextFactory[F, RenderResult]:

  private var _lastContext: Option[SsgMeltContext[F, ?, ?]] = None

  /** The most recently constructed context; populated after [[build]] is called. */
  private[ssg] def lastContext: Option[SsgMeltContext[F, ?, ?]] = _lastContext

  override def build[P <: AnyNamedTuple, B](
    params:      P,
    bodyDecoder: BodyDecoder[B]
  ): MeltContext[F, P, B, RenderResult] =
    val ctx = new SsgMeltContext[F, P, B](
      params,
      requestPath,
      config.template,
      config.manifest,
      config.basePath,
      config.manifest ne ViteManifest.empty,
      config.defaultTitle,
      config.defaultLang
    )
    _lastContext = Some(ctx)
    ctx
