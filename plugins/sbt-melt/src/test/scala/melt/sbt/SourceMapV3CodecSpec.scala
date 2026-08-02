/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.sbt

import melt.sbt.SourceMapV3Codec.*

/** Tests for [[SourceMapV3Codec]] — the full multi-source/multi-segment V3
  * `mappings` codec used to compose linker maps with the MELT source maps.
  */
class SourceMapV3CodecSpec extends munit.FunSuite:

  test("decode resolves cumulative deltas across lines and segments") {
    // "AAAA" = [0,0,0,0]; "CAAC" = [+1,0,0,+1]. Second line resets genCol only.
    val decoded = SourceMapV3Codec.decode("AAAA,CAAC;AACA")
    assertEquals(
      decoded,
      Vector(
        Vector(
          Segment(0, Some(SourceRef(0, 0, 0, None))),
          Segment(1, Some(SourceRef(0, 0, 1, None)))
        ),
        Vector(
          Segment(0, Some(SourceRef(0, 1, 1, None)))
        )
      )
    )
  }

  test("decode handles unmapped (1-field) and named (5-field) segments") {
    val decoded = SourceMapV3Codec.decode("AAAAA,C")
    assertEquals(
      decoded,
      Vector(
        Vector(
          Segment(0, Some(SourceRef(0, 0, 0, Some(0)))),
          Segment(1, None)
        )
      )
    )
  }

  test("decode preserves empty generated lines") {
    val decoded = SourceMapV3Codec.decode("AAAA;;AACA")
    assertEquals(decoded.length, 3)
    assertEquals(decoded(1), Vector.empty[Segment])
  }

  test("round-trip: decode then encode reproduces the mappings string") {
    val samples = List(
      "AAAA,CAAC;AACA",
      "AAAAA,C",
      "AAAA;;AACA",
      "AAgBA,CAAC,EAAE;IAAM,QAAQ"
    )
    samples.foreach { m =>
      assertEquals(SourceMapV3Codec.encode(SourceMapV3Codec.decode(m)), m, s"round-trip: $m")
    }
  }

  test("round-trip: encode then decode reproduces the structured form") {
    val lines = Vector(
      Vector(
        Segment(0, Some(SourceRef(0, 0, 0, None))),
        Segment(5, Some(SourceRef(1, 10, 4, Some(2))))
      ),
      Vector.empty[Segment],
      Vector(
        Segment(2, None),
        Segment(8, Some(SourceRef(0, 3, 0, None)))
      )
    )
    assertEquals(SourceMapV3Codec.decode(SourceMapV3Codec.encode(lines)), lines)
  }

  test("negative deltas (source index/line stepping back) round-trip") {
    val lines = Vector(
      Vector(
        Segment(0, Some(SourceRef(2, 40, 0, None))),
        Segment(4, Some(SourceRef(0, 5, 2, None)))
      )
    )
    assertEquals(SourceMapV3Codec.decode(SourceMapV3Codec.encode(lines)), lines)
  }
