/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.format

import melt.parser.MeltRegionScanner
import melt.parser.MeltRegionScanner.{ Region, RegionKind }

/** Formats a `.melt` file by reformatting each recognized section's inner text
  * and splicing the results back in place — everything outside the sections
  * (tags, template markup, inter-section text) is left byte-identical.
  *
  * Phase 0 ships only the scan + splice skeleton. The default `formatSection`
  * is the identity, so `format(src) == Right(src)` for any well-formed source.
  * Phase 1 wires scalafmt-dynamic for `InstanceScript`/`ModuleScript` and a CSS
  * formatter for `Style`.
  */
object MeltFormatter:

  /** @param formatSection maps `(kind, innerText) => formattedInnerText`. */
  def format(
    source:        String,
    formatSection: (RegionKind, String) => String = (_, inner) => inner
  ): Either[String, String] =
    formatE(source, (r, s) => Right(formatSection(r.kind, s)))

  /** Like [[format]] but the transform receives the whole [[Region]] (so a
    * `<style>` handler can see its `styleLang`) and may fail with `Left(message)`;
    * the first failure short-circuits and the source is not modified. */
  def formatE(
    source:        String,
    formatSection: (Region, String) => Either[String, String]
  ): Either[String, String] =
    MeltRegionScanner.scan(source).flatMap { regions =>
      // Splice from the last region to the first so earlier offsets stay valid.
      regions
        .sortBy(-_.innerStart)
        .foldLeft[Either[String, String]](Right(source)) { (accE, r) =>
          accE.flatMap { acc =>
            formatSection(r, acc.substring(r.innerStart, r.innerEnd)).map { formatted =>
              acc.substring(0, r.innerStart) + formatted + acc.substring(r.innerEnd)
            }
          }
        }
    }
