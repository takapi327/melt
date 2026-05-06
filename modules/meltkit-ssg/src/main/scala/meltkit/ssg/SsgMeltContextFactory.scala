/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import scala.NamedTuple.AnyNamedTuple

import melt.runtime.render.RenderResult

import meltkit.{ MeltContext, MeltContextFactory, Template, ViteManifest }
import meltkit.codec.BodyDecoder

/** [[MeltContextFactory]] implementation for SSG.
  *
  * Passed to [[meltkit.Route.tryHandle]] in place of the http4s factory.
  * After the handler runs, [[lastContext]] exposes the [[SsgMeltContext]]
  * so that [[SsgGenerator]] can read [[SsgMeltContext.capturedHtml]].
  *
  * [[SsgMeltContext]] extends [[ServerMeltContext]] which extends [[MeltContext]],
  * so no `asInstanceOf` cast is required.
  */
final class SsgMeltContextFactory[F[_]](
  requestPath:  String,
  template:     Template,
  manifest:     ViteManifest,
  basePath:     String,
  useHydration: Boolean,
  defaultTitle: String,
  defaultLang:  String
) extends MeltContextFactory[F, RenderResult]:

  private var _lastContext: Option[SsgMeltContext[F, ?, ?]] = None

  /** The most recently constructed context; populated after [[build]] is called. */
  private[ssg] def lastContext: Option[SsgMeltContext[F, ?, ?]] = _lastContext

  override def build[P <: AnyNamedTuple, B](params: P, bodyDecoder: BodyDecoder[B]): MeltContext[F, P, B, RenderResult] =
    val ctx = new SsgMeltContext[F, P, B](
      params,
      requestPath,
      template,
      manifest,
      basePath,
      useHydration,
      defaultTitle,
      defaultLang
    )
    _lastContext = Some(ctx)
    ctx
