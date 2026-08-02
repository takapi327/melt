/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import java.io.File

/** Tests for [[MeltDetectPlugin.findMeltFiles]] — the scan that warns when a
  * project has `.melt` sources but no MeltPlugin enabled.
  */
class MeltDetectSpec extends munit.FunSuite:

  private def withTempDir(test: File => Unit): Unit =
    val dir = java.nio.file.Files.createTempDirectory("melt-detect").toFile
    try test(dir)
    finally
      def rm(f: File): Unit =
        if f.isDirectory then Option(f.listFiles).toList.flatten.foreach(rm)
        f.delete()
      rm(dir)

  private def touch(f: File): Unit =
    f.getParentFile.mkdirs()
    val w = new java.io.PrintWriter(f, "UTF-8")
    try w.write("")
    finally w.close()

  test("finds .melt files recursively under source directories") {
    withTempDir { dir =>
      touch(new File(dir, "components/App.melt"))
      touch(new File(dir, "pages/nested/Home.melt"))
      touch(new File(dir, "components/App.scala"))
      val found = MeltDetectPlugin.findMeltFiles(Seq(dir))
      assertEquals(found.map(_.getName).toSet, Set("App.melt", "Home.melt"))
    }
  }

  test("returns empty when there are no .melt files") {
    withTempDir { dir =>
      touch(new File(dir, "Main.scala"))
      assertEquals(MeltDetectPlugin.findMeltFiles(Seq(dir)), Seq.empty)
    }
  }

  test("skips non-existent directories without failing") {
    withTempDir { dir =>
      val missing = new File(dir, "does-not-exist")
      assertEquals(MeltDetectPlugin.findMeltFiles(Seq(missing)), Seq.empty)
    }
  }
