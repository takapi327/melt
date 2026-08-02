/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Derives stable, per-.melt-URI identifiers for the virtual .scala file that
  * [[MetalsBridge]] hands to Metals.
  *
  * Two `.melt` files that share a basename (e.g. `a/index.melt`, `b/index.melt`)
  * must map to distinct virtual files, and — because every open document's virtual
  * `.scala` is compiled together in one Bloop source set — to distinct top-level
  * names. Both the virtual filename and the wrapping object name therefore include
  * a hash of the full URI so nothing collides. The values are deterministic, so
  * the same document always yields the same virtual identity across requests.
  */
object MeltVirtualId:

  /** Object name that [[VirtualFileGenerator]] wraps the script body in, unique per URI. */
  def objectName(meltUri: String): String = "Melt_" + hash(meltUri)

  /** Base filename (without extension) for the virtual `.scala`, unique per URI but
    * keeping the original basename for readability. */
  def fileBaseName(meltUri: String): String =
    val basename = meltUri.replaceAll(".*[/\\\\]", "").stripSuffix(".melt")
    val safe     = basename.replaceAll("[^A-Za-z0-9_]", "_")
    s"${ safe }_${ hash(meltUri) }"

  private def hash(s: String): String =
    val digest = MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8))
    digest.take(5).map(b => f"${ b & 0xff }%02x").mkString
