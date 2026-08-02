/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import java.nio.file.{ Files, Path }

import scala.jdk.CollectionConverters.*
import scala.util.Try

import com.google.gson.JsonParser

/** Resolves a real compile classpath and Scala version for type-checking `.melt`
  * script sections, by reading the user project's Bloop config.
  *
  * Without this, [[MetalsBridge]] hands Metals a synthetic project with an empty
  * classpath, so `melt.runtime.*` (State/Signal/Bind/…) and the user's own
  * dependencies cannot be resolved — producing "Not found" false diagnostics for
  * essentially every real component. Reusing the project's already-resolved Bloop
  * classpath makes those symbols available.
  *
  * When Metals is backed by Bloop (its default), `bloopInstall` writes
  * `.bloop/<project>.json` with a full `classpath`. Projects driven by sbt/Mill
  * BSP have no such files; in that case only the Scala version is recovered
  * (inferred from the `.bloop/<project>/scala-X.Y.Z` output directory names) and
  * the classpath stays empty.
  */
object BloopClasspathResolver:

  /** @param classpath    absolute jar/dir entries for the compile classpath (may be empty)
    * @param scalaVersion the project's Scala version, when it could be determined
    */
  final case class Resolved(classpath: List[String], scalaVersion: Option[String])

  private val ScalaDir = """scala-(\d+\.\d+\.\d+\S*)""".r

  /** Reads `.bloop/` under [workspaceRoot] and returns a best-effort classpath +
    * Scala version. Never throws; returns an empty result on any failure. */
  def resolve(workspaceRoot: Path): Resolved =
    val bloopDir = workspaceRoot.resolve(".bloop")
    val projects = readProjects(bloopDir)
    val selected = selectProject(projects)
    Resolved(
      classpath    = selected.map(_.classpath).getOrElse(Nil),
      scalaVersion = selected.flatMap(_.scalaVersion).orElse(inferScalaVersion(bloopDir))
    )

  private final case class Proj(name: String, classpath: List[String], scalaVersion: Option[String])

  private def readProjects(bloopDir: Path): List[Proj] =
    if !Files.isDirectory(bloopDir) then Nil
    else
      Try {
        val stream = Files.list(bloopDir)
        try
          stream
            .iterator()
            .asScala
            .filter(p => p.getFileName.toString.endsWith(".json"))
            .filterNot(_.getFileName.toString == "bloop.settings.json")
            .flatMap(parseProject)
            .toList
        finally stream.close()
      }.getOrElse(Nil)

  private def parseProject(jsonPath: Path): Option[Proj] =
    Try {
      val root      = JsonParser.parseString(Files.readString(jsonPath)).getAsJsonObject
      val proj      = root.getAsJsonObject("project")
      val name      = Option(proj.get("name")).map(_.getAsString).getOrElse(jsonPath.getFileName.toString)
      val classpath =
        Option(proj.getAsJsonArray("classpath"))
          .map(_.asScala.map(_.getAsString).toList)
          .getOrElse(Nil)
      val scalaVersion =
        Option(proj.getAsJsonObject("scala")).flatMap(s => Option(s.get("version"))).map(_.getAsString)
      Proj(name, classpath, scalaVersion)
    }.toOption

  /** Picks the project most likely to be the one authoring the `.melt` files:
    * one whose classpath already carries `melt-runtime`, breaking ties (and the
    * no-melt-runtime case) by the largest classpath. */
  private def selectProject(projects: List[Proj]): Option[Proj] =
    if projects.isEmpty then None
    else
      val withRuntime = projects.filter(_.classpath.exists(_.contains("melt-runtime")))
      val pool        = if withRuntime.nonEmpty then withRuntime else projects
      Some(pool.maxBy(_.classpath.size))

  /** Infers the Scala version from `.bloop/<project>/scala-X.Y.Z` output directory
    * names, so sbt/Mill BSP projects (which have no Bloop json files) still get the
    * right compiler version even when the classpath cannot be recovered. */
  private def inferScalaVersion(bloopDir: Path): Option[String] =
    if !Files.isDirectory(bloopDir) then None
    else
      Try {
        val stream = Files.walk(bloopDir, 4)
        try
          stream
            .iterator()
            .asScala
            .map(_.getFileName)
            .filter(_ != null)
            .map(_.toString)
            .collectFirst { case ScalaDir(v) => v }
        finally stream.close()
      }.toOption.flatten
