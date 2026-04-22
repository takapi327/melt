/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths }

/** JVM-specific factory methods for [[ViteManifest]]. */
extension (obj: ViteManifest.type)

  /** Loads the manifest from a filesystem path string. */
  def load(path: String, uriPrefix: String = "scalajs"): ViteManifest =
    obj.loadFromPath(Paths.get(path), uriPrefix)

  /** Loads the manifest from a NIO [[java.nio.file.Path]]. */
  def loadFromPath(path: Path, uriPrefix: String = "scalajs"): ViteManifest =
    if !Files.exists(path) then
      throw new FileNotFoundException(
        s"Vite manifest not found at '$path'. Did you run `vite build`?"
      )
    val json = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    obj.fromString(json, uriPrefix)
