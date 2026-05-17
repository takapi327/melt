/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import meltkit.ssg.NodeFsSsg

package object syntax:

  extension (obj: Template.type)
    /** Reads a template from the file at `path` (UTF-8 encoded). */
    def fromFile(path: String): Template = obj.fromString(NodeFsSsg.readFileSync(path, "utf8"))

  extension (obj: ViteManifest.type)
    /** Reads and parses a Vite manifest from the file at `path`. */
    def fromFile(path: String, uriPrefix: String = "scalajs"): ViteManifest =
      obj.fromString(NodeFsSsg.readFileSync(path, "utf8"), uriPrefix)
