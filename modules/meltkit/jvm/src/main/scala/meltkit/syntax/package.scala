/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import java.nio.file.{ Files, Path }

package object syntax:

  extension (obj: Template.type)
    /** Reads a template from the file at `path` using the platform default charset. */
    def fromFile(path: Path): Template = obj.fromString(Files.readString(path))

  extension (obj: ViteManifest.type)
    /** Reads and parses a Vite manifest from the file at `path`. */
    def fromFile(path: Path, uriPrefix: String = "scalajs"): ViteManifest =
      obj.fromString(Files.readString(path), uriPrefix)
