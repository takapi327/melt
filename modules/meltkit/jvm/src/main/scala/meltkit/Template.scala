/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

import java.io.FileNotFoundException
import java.nio.charset.StandardCharsets

import scala.io.Source

/** JVM-specific factory methods for [[Template]]. */
extension (obj: Template.type)

  /** Loads a [[Template]] from the classpath.
    *
    * Place the file at `src/main/resources/index.html` (sbt layout) and load
    * it with `Template.fromResource("/index.html")`.
    *
    * @throws java.io.FileNotFoundException if the resource is not on the classpath.
    */
  def fromResource(path: String): Template =
    val normalised = if path.startsWith("/") then path else s"/$path"
    val stream     = Template.getClass.getResourceAsStream(normalised)
    if stream == null then
      throw new FileNotFoundException(
        s"Template resource '$normalised' not found on the classpath. " +
          s"Create the file at 'src/main/resources$normalised' in your sbt project."
      )
    try Template.fromString(Source.fromInputStream(stream, StandardCharsets.UTF_8.name).mkString)
    finally stream.close()
