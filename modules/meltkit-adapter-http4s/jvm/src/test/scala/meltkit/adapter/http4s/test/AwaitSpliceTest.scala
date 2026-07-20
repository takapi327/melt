/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.adapter.http4s.test

import melt.runtime.render.RenderResult

import meltkit.SsrRenderScope

/** Unit tests for the async-SSR marker splicing + hydration seed injection
  * (`SsrRenderScope.spliceAndSeed`). */
class AwaitSpliceTest extends munit.FunSuite:

  private def frag(body: String): RenderResult = RenderResult(body, "")

  test("splices a resolved fragment over its marker span (marker + pending)"):
    val body     = "<main><!--melt:sb:melt-sb-1--><p>Loading…</p><!--/melt:sb:melt-sb-1--></main>"
    val resolved = SsrRenderScope.Resolved(List("melt-sb-1" -> frag("<ul><li>a</li></ul>")), "")
    val out      = SsrRenderScope.spliceAndSeed(body, resolved)
    assertEquals(out, "<main><ul><li>a</li></ul></main>")

  test("splices multiple markers independently"):
    val body =
      "<!--melt:sb:melt-sb-1-->x<!--/melt:sb:melt-sb-1--> <!--melt:sb:melt-sb-2-->y<!--/melt:sb:melt-sb-2-->"
    val resolved = SsrRenderScope.Resolved(
      List("melt-sb-1" -> frag("<a/>"), "melt-sb-2" -> frag("<b/>")),
      ""
    )
    assertEquals(SsrRenderScope.spliceAndSeed(body, resolved), "<a/> <b/>")

  test("appends the hydration seed as a data-melt-queries script, escaping </"):
    val resolved = SsrRenderScope.Resolved(Nil, """{"k":"</script>"}""")
    val out      = SsrRenderScope.spliceAndSeed("<main></main>", resolved)
    assert(out.contains("""<script type="application/json" data-melt-queries>"""), out)
    assert(out.contains("""<\/script>"""), out) // the value's </ is escaped so it can't close the tag early
    assert(out.endsWith("</script>"), out)      // exactly one real closing tag, at the very end
    // the escaped value carries no raw </script> breakout before the closing tag
    assert(!out.dropRight("</script>".length).contains("</script>"), out)

  test("no seed → no script element appended"):
    val out = SsrRenderScope.spliceAndSeed("<main></main>", SsrRenderScope.Resolved(Nil, ""))
    assertEquals(out, "<main></main>")

  test("a missing marker leaves the body unchanged"):
    val resolved = SsrRenderScope.Resolved(List("melt-sb-9" -> frag("<x/>")), "")
    assertEquals(SsrRenderScope.spliceAndSeed("<main>plain</main>", resolved), "<main>plain</main>")
