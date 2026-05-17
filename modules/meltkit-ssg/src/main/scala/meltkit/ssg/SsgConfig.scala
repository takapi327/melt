/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.ssg

import java.nio.file.Path

import meltkit.{ Template, ViteManifest }

/** Configuration for a static site generation run.
  *
  * @param outputDir    directory where generated HTML files are written
  * @param template     HTML shell template applied to every generated page
  * @param manifest     Vite asset manifest used for JS/CSS chunk injection;
  *                     use [[meltkit.ViteManifest.empty]] when hydration is not needed
  * @param assetsDir    optional Vite `dist/assets` directory to copy into `outputDir/assets`
  * @param publicDir    optional public directory whose contents are copied verbatim to `outputDir`
  * @param cleanOutput  when `true`, `outputDir` is deleted and recreated before generation
  * @param quiet        when `true`, per-page progress messages are suppressed
  * @param basePath     deployment root path (e.g. `""` for root, `"/myapp"` for sub-path)
  * @param defaultTitle default page title; when empty, `RenderResult.title` is used
  * @param defaultLang  default HTML `lang` attribute (default: `"en"`)
  */
final case class SsgConfig(
  outputDir:    Path,
  template:     Template,
  manifest:     ViteManifest = ViteManifest.empty,
  assetsDir:    Option[Path] = None,
  publicDir:    Option[Path] = None,
  cleanOutput:  Boolean      = true,
  quiet:        Boolean      = false,
  basePath:     String       = "",
  defaultTitle: String       = "",
  defaultLang:  String       = "en"
)
