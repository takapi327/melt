/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import java.io.File

import melt.sbt.MiniJson.*
import melt.sbt.SourceMapV3Codec.*

/** Tests for [[SourceMapComposer]] — splicing `.melt` sources into a linker map. */
class SourceMapComposerSpec extends munit.FunSuite:

  private val counterMeta =
    MeltGeneratedSource.Meta("Counter.melt", IndexedSeq((5, 3, 2), (10, 7, 4)))

  private def metaFor(path: String): Option[MeltGeneratedSource.Meta] =
    if path == "Counter.scala" then Some(counterMeta) else None

  /** Builds a linker map: source 0 is hand-written, source 1 is melt-generated. */
  private def linkerMap(mappings: String): String =
    MiniJson.render(
      JObj(
        Vector(
          "version"  -> JNum("3"),
          "file"     -> JStr("main.js"),
          "sources"  -> JArr(Vector(JStr("App.scala"), JStr("Counter.scala"))),
          "names"    -> JArr(Vector.empty),
          "mappings" -> JStr(mappings)
        )
      )
    )

  test("returns the map unchanged when no source is melt-generated") {
    val json = MiniJson.render(
      JObj(Vector("sources" -> JArr(Vector(JStr("App.scala"))), "mappings" -> JStr("AAAA")))
    )
    assertEquals(SourceMapComposer.compose(json, _ => None), json)
  }

  test("splices the .melt source and remaps segments that hit generated Scala") {
    val mappings = SourceMapV3Codec.encode(
      Vector(
        Vector(Segment(0, Some(SourceRef(0, 0, 0, None)))), // → App.scala, untouched
        Vector(Segment(0, Some(SourceRef(1, 4, 0, None))))  // → Counter.scala line 5 (0-based 4)
      )
    )
    val composed = SourceMapComposer.compose(linkerMap(mappings), metaFor)
    val obj      = MiniJson.parse(composed).asObject

    assertEquals(
      obj.field("sources").asArray.map(_.asString),
      Vector("App.scala", "Counter.scala", "Counter.melt")
    )

    val decoded = SourceMapV3Codec.decode(obj.field("mappings").asString)
    // Hand-written segment untouched.
    assertEquals(decoded(0), Vector(Segment(0, Some(SourceRef(0, 0, 0, None)))))
    // Generated segment now points at Counter.melt (index 2), line 3→0-based 2, col 2→0-based 1.
    assertEquals(decoded(1), Vector(Segment(0, Some(SourceRef(2, 2, 1, None)))))
  }

  test("re-composing an already-composed map is a no-op (no duplicate .melt source)") {
    val mappings = SourceMapV3Codec.encode(
      Vector(Vector(Segment(0, Some(SourceRef(1, 4, 0, None)))))
    )
    val once  = SourceMapComposer.compose(linkerMap(mappings), metaFor)
    val twice = SourceMapComposer.compose(once, metaFor)
    assertEquals(twice, once)
    assertEquals(
      MiniJson.parse(twice).asObject.field("sources").asArray.count(_.asString.endsWith(".melt")),
      1
    )
  }

  test("a meta with no V3 entries is ignored — no dead .melt source is spliced") {
    val emptyMeta = MeltGeneratedSource.Meta("Legacy.melt", IndexedSeq.empty)
    val mappings  = SourceMapV3Codec.encode(Vector(Vector(Segment(0, Some(SourceRef(1, 4, 0, None))))))
    val composed  = SourceMapComposer.compose(linkerMap(mappings), _ => Some(emptyMeta))
    assertEquals(composed, linkerMap(mappings))
  }

  test("keeps the Scala segment when mapPosition finds no entry") {
    // srcLine 0 (0-based) = generated line 1, before the first MELT entry (genLine 5).
    val mappings = SourceMapV3Codec.encode(
      Vector(Vector(Segment(0, Some(SourceRef(1, 0, 0, None)))))
    )
    val composed = SourceMapComposer.compose(linkerMap(mappings), metaFor)
    val decoded  = SourceMapV3Codec.decode(MiniJson.parse(composed).asObject.field("mappings").asString)
    assertEquals(decoded(0), Vector(Segment(0, Some(SourceRef(1, 0, 0, None)))))
  }

  test("composeFile rewrites a .js.map on disk, splicing the resolved .melt") {
    val dir       = java.nio.file.Files.createTempDirectory("melt-cmp").toFile
    val meltFile  = new File(dir, "Widget.melt")
    val scalaFile = new File(dir, "Widget.scala")
    val jsMapFile = new File(dir, "main.js.map")
    try
      writeFile(meltFile, "<h1>hi</h1>")

      // MELT block: generated Scala line 2 → .melt line 3, col 2.
      val meltMappings = SourceMapV3Codec.encode(
        Vector(Vector.empty, Vector(Segment(0, Some(SourceRef(0, 2, 1, None)))))
      )
      val v3Json =
        s"""{"version":3,"sources":["${ meltFile.getAbsolutePath }"],"names":[],"mappings":"$meltMappings"}"""
      val b64 = java.util.Base64.getEncoder.encodeToString(v3Json.getBytes("UTF-8"))
      writeFile(
        scalaFile,
        s"""object Widget { val x = 1 }
           |/*
           |    -- MELT GENERATED --
           |    SOURCE: ${ meltFile.getAbsolutePath }
           |    V3: $b64
           |    -- MELT GENERATED --
           |*/
           |""".stripMargin
      )

      // Linker map: one segment pointing at Widget.scala line 2 (0-based srcLine 1).
      val linkerMappings = SourceMapV3Codec.encode(
        Vector(Vector(Segment(0, Some(SourceRef(0, 1, 0, None)))))
      )
      writeFile(
        jsMapFile,
        MiniJson.render(
          JObj(
            Vector(
              "version"  -> JNum("3"),
              "sources"  -> JArr(Vector(JStr("Widget.scala"))),
              "names"    -> JArr(Vector.empty),
              "mappings" -> JStr(linkerMappings)
            )
          )
        )
      )

      assert(SourceMapComposer.composeFile(jsMapFile), "expected the map to change")

      val obj = MiniJson.parse(readFile(jsMapFile)).asObject
      assertEquals(
        obj.field("sources").asArray.map(_.asString),
        Vector("Widget.scala", meltFile.getAbsolutePath)
      )
      val decoded = SourceMapV3Codec.decode(obj.field("mappings").asString)
      assertEquals(decoded(0), Vector(Segment(0, Some(SourceRef(1, 2, 1, None)))))
    finally
      List(meltFile, scalaFile, jsMapFile).foreach(_.delete())
      dir.delete()
  }

  test("composeFile resolves a file:// absolute source (real linker format)") {
    val dir       = java.nio.file.Files.createTempDirectory("melt-cmp2").toFile
    val meltFile  = new File(dir, "Card.melt")
    val scalaFile = new File(dir, "Card.scala")
    val jsMapFile = new File(dir, "main.js.map")
    try
      writeFile(meltFile, "<div/>")
      val meltMappings = SourceMapV3Codec.encode(
        Vector(Vector.empty, Vector(Segment(0, Some(SourceRef(0, 4, 0, None)))))
      )
      val v3Json =
        s"""{"version":3,"sources":["${ meltFile.getAbsolutePath }"],"names":[],"mappings":"$meltMappings"}"""
      val b64 = java.util.Base64.getEncoder.encodeToString(v3Json.getBytes("UTF-8"))
      writeFile(
        scalaFile,
        s"""object Card {}
           |/*
           |    -- MELT GENERATED --
           |    SOURCE: ${ meltFile.getAbsolutePath }
           |    V3: $b64
           |    -- MELT GENERATED --
           |*/
           |""".stripMargin
      )
      val linkerMappings = SourceMapV3Codec.encode(
        Vector(Vector(Segment(0, Some(SourceRef(0, 1, 0, None)))))
      )
      writeFile(
        jsMapFile,
        MiniJson.render(
          JObj(
            Vector(
              "sources"  -> JArr(Vector(JStr(s"file://${ scalaFile.getAbsolutePath }"))),
              "mappings" -> JStr(linkerMappings)
            )
          )
        )
      )

      assert(SourceMapComposer.composeFile(jsMapFile))
      val sources = MiniJson.parse(readFile(jsMapFile)).asObject.field("sources").asArray.map(_.asString)
      assertEquals(sources(1), meltFile.getAbsolutePath)
    finally
      List(meltFile, scalaFile, jsMapFile).foreach(_.delete())
      dir.delete()
  }

  private def writeFile(f: File, content: String): Unit =
    val w = new java.io.PrintWriter(f, "UTF-8")
    try w.write(content)
    finally w.close()

  private def readFile(f: File): String =
    val src = scala.io.Source.fromFile(f, "UTF-8")
    try src.mkString
    finally src.close()

  test("appends sourcesContent for .melt when the linker map carries it") {
    val mappings = SourceMapV3Codec.encode(
      Vector(Vector(Segment(0, Some(SourceRef(1, 4, 0, None)))))
    )
    val withContent = MiniJson.render(
      JObj(
        Vector(
          "sources"        -> JArr(Vector(JStr("App.scala"), JStr("Counter.scala"))),
          "sourcesContent" -> JArr(Vector(JStr("// app"), JStr("// counter"))),
          "mappings"       -> JStr(mappings)
        )
      )
    )
    val composed =
      SourceMapComposer.compose(withContent, metaFor, p => if p == "Counter.melt" then Some("<h1/>") else None)
    val content = MiniJson.parse(composed).asObject.field("sourcesContent").asArray
    assertEquals(content.length, 3)
    assertEquals(content(2).asString, "<h1/>")
  }

  test("composeFile skips an empty .js.map instead of throwing") {
    val dir       = java.nio.file.Files.createTempDirectory("melt-cmp-empty").toFile
    val jsMapFile = new File(dir, "empty.js.map")
    try
      writeFile(jsMapFile, "")
      assertEquals(SourceMapComposer.composeFile(jsMapFile), false)
      assertEquals(readFileForTest(jsMapFile), "")
    finally
      jsMapFile.delete()
      dir.delete()
  }

  test("composeFile leaves a malformed .js.map untouched instead of throwing") {
    val dir       = java.nio.file.Files.createTempDirectory("melt-cmp-bad").toFile
    val jsMapFile = new File(dir, "bad.js.map")
    try
      writeFile(jsMapFile, """{"version":3,"sources":""")
      assertEquals(SourceMapComposer.composeFile(jsMapFile), false)
      assertEquals(readFileForTest(jsMapFile), """{"version":3,"sources":""")
    finally
      jsMapFile.delete()
      dir.delete()
  }

  private def readFileForTest(f: File): String =
    new String(java.nio.file.Files.readAllBytes(f.toPath), "UTF-8")
