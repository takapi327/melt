/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** JVM-only extension methods for [[Template]] that load content from the
  * classpath, allowing the HTML shell to be stored as a resource file instead
  * of an inline string.
  *
  * {{{
  * // src/main/resources/index.html
  * override val template: Template = Template.fromResource("index.html")
  * }}}
  */
extension (t: Template.type)

  /** Loads a [[Template]] from a classpath resource.
    *
    * The resource is looked up via the context class loader, which includes
    * all JARs and `src/main/resources` directories on the classpath.
    *
    * @param path resource path relative to the classpath root (e.g. `"index.html"`)
    * @throws RuntimeException if the resource is not found
    */
  def fromResource(path: String): Template =
    val stream = Option(Thread.currentThread().getContextClassLoader.getResourceAsStream(path))
      .getOrElse(sys.error(s"[meltkit] Template resource not found: $path"))
    val content =
      try scala.io.Source.fromInputStream(stream, "UTF-8").mkString
      finally stream.close()
    Template.fromString(content)
