/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import meltc.{ CompileMode, MeltCompiler }

/** Phase B §12.3.11 — SecurityChecker warnings surfaced through
  * `MeltCompiler.compile`.
  */
class SecurityCheckerSpec extends munit.FunSuite:

  private def warnings(src: String, mode: CompileMode = CompileMode.SSR): List[String] =
    MeltCompiler.compile(src, "App.melt", "App", "", mode).warnings.map(_.message)

  test("iframe with dynamic src is flagged") {
    val ws = warnings("""<iframe src={trusted}></iframe>""")
    assert(ws.exists(m => m.contains("<iframe>") && m.contains("dynamic `src`")), ws)
  }

  test("iframe with static src is NOT flagged") {
    val ws = warnings("""<iframe src="https://example.com/x"></iframe>""")
    assert(!ws.exists(m => m.contains("dynamic `src`")), ws)
  }

  test("iframe with bind:src is also flagged") {
    val ws = warnings("""<iframe bind:src={url}></iframe>""")
    assert(ws.exists(m => m.contains("<iframe>") && m.contains("dynamic `src`")), ws)
  }

  test("iframe srcdoc with dynamic value is a compile error (S-5)") {
    val result = MeltCompiler.compile("""<iframe srcdoc={html}></iframe>""", "App.melt", "App", "", CompileMode.SSR)
    assert(result.errors.exists(_.message.contains("srcdoc")), result.errors)
    assert(result.scalaCode.isEmpty, "should not generate code when srcdoc error present")
  }

  test("iframe srcdoc error fires in SPA mode too (S-5)") {
    val result = MeltCompiler.compile("""<iframe srcdoc={html}></iframe>""", "App.melt", "App", "", CompileMode.SPA)
    assert(result.errors.exists(_.message.contains("srcdoc")), result.errors)
  }

  test("iframe with static srcdoc is not flagged (S-5)") {
    val result =
      MeltCompiler.compile("""<iframe srcdoc="<p>safe</p>"></iframe>""", "App.melt", "App", "", CompileMode.SSR)
    assert(result.errors.isEmpty, result.errors)
  }

  test("object with dynamic data is flagged") {
    val ws = warnings("""<object data={u}></object>""")
    assert(ws.exists(_.contains("<object")), ws)
  }

  test("embed with dynamic src is flagged") {
    val ws = warnings("""<embed src={u}/>""")
    // <embed> IS a void element so self-close is fine here.
    assert(ws.exists(_.contains("<embed")), ws)
  }

  test("form action={...} is flagged") {
    val ws = warnings("""<form action={u}><input/></form>""")
    assert(ws.exists(_.contains("<form")), ws)
  }

  test("button formaction={...} is flagged") {
    val ws = warnings("""<form><button formaction={u}>Go</button></form>""")
    assert(ws.exists(_.contains("formaction")), ws)
  }

  test("meta http-equiv=refresh with dynamic content is flagged") {
    val ws = warnings("""<meta http-equiv="refresh" content={url}/>""")
    assert(ws.exists(m => m.contains("meta") && m.toLowerCase.contains("refresh")), ws)
  }

  test("meta http-equiv=refresh with STATIC content is NOT flagged") {
    val ws = warnings("""<meta http-equiv="refresh" content="5; url=/login"/>""")
    assert(!ws.exists(_.contains("refresh")), ws)
  }

  test("a target=_blank without rel=noopener is flagged (tabnabbing)") {
    val ws = warnings("""<a href="/x" target="_blank">x</a>""")
    assert(ws.exists(m => m.contains("_blank") && m.contains("noopener")), ws)
  }

  test("a target=_blank with rel=noopener is NOT flagged") {
    val ws = warnings("""<a href="/x" target="_blank" rel="noopener">x</a>""")
    assert(!ws.exists(_.contains("noopener")), ws)
  }

  test("a target=_blank with rel=\"noopener noreferrer\" is NOT flagged") {
    val ws = warnings("""<a href="/x" target="_blank" rel="noopener noreferrer">x</a>""")
    assert(!ws.exists(_.contains("noopener")), ws)
  }

  test("plain <a href> without target=_blank is NOT flagged") {
    val ws = warnings("""<a href="/x">home</a>""")
    assert(!ws.exists(_.contains("_blank")), ws)
  }

  test("security warnings also fire in SPA mode") {
    val ws = warnings("""<iframe src={u}></iframe>""", CompileMode.SPA)
    assert(ws.exists(m => m.contains("<iframe>") && m.contains("dynamic `src`")), ws)
  }
