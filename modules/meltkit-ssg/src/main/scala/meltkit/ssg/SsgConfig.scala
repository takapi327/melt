/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.Path

/** Configuration for a static site generation run.
  *
  * @param outputDir   directory where generated HTML files are written
  * @param assetsDir   optional Vite `dist/assets` directory to copy alongside the HTML
  * @param publicDir   optional directory whose contents are copied verbatim to the output
  *                    root (e.g. `public/` → `{outputDir}/`).
  *                    Use this to ship static files such as `styles/global.css` that are
  *                    referenced by `import "/styles/global.css"` in `.melt` components.
  * @param cleanOutput when `true`, `outputDir` is deleted and recreated before generation
  * @param quiet       when `true`, per-page progress messages are suppressed
  */
final case class SsgConfig(
  outputDir:   Path,
  assetsDir:   Option[Path] = None,
  publicDir:   Option[Path] = None,
  cleanOutput: Boolean      = true,
  quiet:       Boolean      = false
)
