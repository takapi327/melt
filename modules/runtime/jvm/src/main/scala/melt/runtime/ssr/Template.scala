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

import melt.runtime.Escape

/** A SvelteKit-style HTML template with `%melt.X%` placeholders.
  *
  * Load the template once at application startup with one of the factory
  * methods in the companion object, then reuse it across requests.
  * Instances are immutable and thread-safe (see
  * `docs/meltc-ssr-design.md` §12.3.3).
  *
  * == Placeholders ==
  *
  *   - `%melt.head%`  — replaced with `RenderResult.head` (the component
  *     `<melt:head>` content plus collected `<style id="melt-…">` blocks)
  *   - `%melt.body%`  — replaced with `RenderResult.body`
  *   - `%melt.title%` — replaced with the HTML-escaped `title` argument
  *   - `%melt.lang%`  — replaced with the attribute-escaped `lang` argument
  *   - `%melt.X%`     — replaced with the HTML-escaped value of key `X`
  *     in the `vars` map passed to [[render]]
  *
  * == Example ==
  *
  * `src/main/resources/index.html`:
  * {{{
  * <!doctype html>
  * <html lang="%melt.lang%">
  *   <head>
  *     <meta charset="UTF-8">
  *     <meta name="viewport" content="width=device-width, initial-scale=1">
  *     <title>%melt.title%</title>
  *     %melt.head%
  *   </head>
  *   <body>
  *     %melt.body%
  *   </body>
  * </html>
  * }}}
  *
  * Application:
  * {{{
  * object Server extends IOApp.Simple:
  *   private val template = Template.fromResource("/index.html")
  *
  *   private val routes: HttpApp[IO] = HttpRoutes.of[IO] {
  *     case GET -> Root =>
  *       val result = components.Home.render()
  *       Ok(
  *         template.render(result, title = "Home"),
  *         `Content-Type`(MediaType.text.html, Charset.`UTF-8`)
  *       )
  *   }.orNotFound
  * }}}
  *
  * == Character encoding ==
  *
  * `Template` performs no charset handling — it assumes the template file
  * is UTF-8 and substitutes UTF-8 strings. Include a `<meta charset="UTF-8">`
  * in your template and set `Content-Type: text/html; charset=utf-8` on the
  * HTTP response. Following Svelte 5 / SvelteKit, duplicate `<meta charset>`
  * declarations inside `%melt.head%` are not detected — rely on HTML5's
  * "first wins" semantics by placing the charset meta near the top of
  * your template.
  */
final class Template private (private val raw: String):

  /** Substitutes the template placeholders and returns the resulting HTML.
    *
    * @param result the component render result
    * @param title  page title (HTML-escaped before substitution). Pass an
    *               empty string to skip
    * @param lang   value for `%melt.lang%`, default `"en"`
    * @param vars   extra placeholder values. Each entry `k -> v` replaces
    *               `%melt.k%` with `Escape.html(v)`. Reserved names (`head`,
    *               `body`, `title`, `lang`) are ignored
    */
  def render(
    result: RenderResult,
    title:  String              = "",
    lang:   String              = "en",
    vars:   Map[String, String] = Map.empty
  ): String =
    var out = raw
    out = out.replace("%melt.lang%",  Escape.attr(lang))
    out = out.replace("%melt.title%", Escape.html(title))
    out = out.replace("%melt.head%",  result.head)
    out = out.replace("%melt.body%",  result.body)
    vars.foreach { case (k, v) =>
      // Reserved keys are ignored so user-provided maps cannot override
      // structural placeholders.
      if k != "head" && k != "body" && k != "title" && k != "lang" then
        out = out.replace(s"%melt.$k%", Escape.html(v))
    }
    out

/** Factory methods for [[Template]]. */
object Template:

  /** Builds a template from an in-memory string. Primarily for tests. */
  def fromString(content: String): Template = new Template(content)

  /** Reads a template from a filesystem path. */
  def fromFile(path: Path): Template =
    new Template(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))

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
    val stream     = getClass.getResourceAsStream(normalised)
    if stream == null then
      throw new FileNotFoundException(
        s"Template resource '$normalised' not found on the classpath. " +
          s"Create the file at 'src/main/resources$normalised' in your sbt project."
      )
    try new Template(Source.fromInputStream(stream, StandardCharsets.UTF_8.name).mkString)
    finally stream.close()
