/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.lsp

import java.nio.file.{ Files, Path }

/** Tests for [[BloopClasspathResolver]] — recovering a real classpath + Scala
  * version from a project's `.bloop/` so Metals can resolve `melt.runtime.*`.
  */
class BloopClasspathResolverSpec extends munit.FunSuite:

  private def withWorkspace(test: Path => Unit): Unit =
    val dir = Files.createTempDirectory("melt-bloop-test")
    try test(dir)
    finally
      def rm(p: Path): Unit =
        if Files.isDirectory(p) then
          val s = Files.list(p);
          try s.iterator().forEachRemaining(rm)
          finally s.close()
        Files.deleteIfExists(p)
      rm(dir)

  private def writeProject(bloopDir: Path, name: String, classpath: List[String], scalaVersion: String): Unit =
    Files.createDirectories(bloopDir)
    val cp   = classpath.map(e => "\"" + e + "\"").mkString(",")
    val json =
      s"""{"version":"1.4.0","project":{"name":"$name","classpath":[$cp],"scala":{"version":"$scalaVersion"}}}"""
    Files.writeString(bloopDir.resolve(s"$name.json"), json)

  test("resolves the classpath and Scala version from a Bloop project") {
    withWorkspace { root =>
      val bloop = root.resolve(".bloop")
      writeProject(bloop, "app", List("/libs/melt-runtime_sjs1_3.jar", "/libs/cats.jar"), "3.8.4")
      val r = BloopClasspathResolver.resolve(root)
      assertEquals(r.classpath, List("/libs/melt-runtime_sjs1_3.jar", "/libs/cats.jar"))
      assertEquals(r.scalaVersion, Some("3.8.4"))
    }
  }

  test("prefers the project carrying melt-runtime over a larger unrelated project") {
    withWorkspace { root =>
      val bloop = root.resolve(".bloop")
      writeProject(bloop, "big", List("/a.jar", "/b.jar", "/c.jar", "/d.jar"), "3.3.4")
      writeProject(bloop, "client", List("/libs/melt-runtime_sjs1_3.jar", "/e.jar"), "3.8.4")
      val r = BloopClasspathResolver.resolve(root)
      assert(r.classpath.exists(_.contains("melt-runtime")), r.classpath.toString)
      assertEquals(r.scalaVersion, Some("3.8.4"))
    }
  }

  test("skips bloop.settings.json and tolerates malformed json") {
    withWorkspace { root =>
      val bloop = root.resolve(".bloop")
      writeProject(bloop, "app", List("/libs/melt-runtime_3.jar"), "3.8.4")
      Files.writeString(bloop.resolve("bloop.settings.json"), """{"javaSemanticDBVersion":"x"}""")
      Files.writeString(bloop.resolve("broken.json"), "{ not json")
      val r = BloopClasspathResolver.resolve(root)
      assertEquals(r.classpath, List("/libs/melt-runtime_3.jar"))
    }
  }

  test("returns an empty result when there is no .bloop directory") {
    withWorkspace { root =>
      assertEquals(BloopClasspathResolver.resolve(root), BloopClasspathResolver.Resolved(Nil, None))
    }
  }

  test("infers the Scala version from output dir names when there are no project json files") {
    withWorkspace { root =>
      // sbt/Mill BSP layout: .bloop/<project>/scala-X.Y.Z output dirs, no *.json.
      Files.createDirectories(root.resolve(".bloop").resolve("root").resolve("scala-3.8.4"))
      val r = BloopClasspathResolver.resolve(root)
      assertEquals(r.classpath, Nil)
      assertEquals(r.scalaVersion, Some("3.8.4"))
    }
  }
