/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime.ssr

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import scala.io.Source

/** JVM-specific factory methods for [[Template]]. */
extension (obj: Template.type)

  /** Reads a template from a filesystem path. */
  def fromFile(path: Path): Template =
    obj.fromString(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))

  /** Loads a template from the classpath.
    *
    * Use this for templates bundled inside the application JAR: place the
    * file at `src/main/resources/index.html` (sbt layout) and load it with
    * `Template.fromResource("/index.html")`.
    *
    * @throws java.io.FileNotFoundException if the resource is not on the
    *         classpath. The exception message includes the expected sbt
    *         layout location to help users recover.
    */
  def fromResource(path: String): Template =
    val normalised = if path.startsWith("/") then path else s"/$path"
    val stream     = obj.getClass.getResourceAsStream(normalised)
    if stream == null then
      throw new FileNotFoundException(
        s"Template resource '$normalised' not found on the classpath. " +
          s"Create the file at 'src/main/resources$normalised' in your sbt project."
      )
    try obj.fromString(Source.fromInputStream(stream, StandardCharsets.UTF_8.name).mkString)
    finally stream.close()
