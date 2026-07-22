/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.analysis

import melt.MeltCompiler

class FormBindingCheckerSpec extends munit.FunSuite:

  private def warnings(src: String): List[String] =
    MeltCompiler.compile(src, "Test.melt", "Test", "").warnings.map(_.message)

  test("use:form on a non-form element warns") {
    val w = warnings("""<div use:form={form}><input name="email"/></div>""")
    assert(w.exists(_.contains("only meaningful on a <form>")), w.toString)
  }

  test("use:form on a <form> element does not warn about placement") {
    val w = warnings("""<form use:form={form}><input name="email"/></form>""")
    assert(!w.exists(_.contains("only meaningful on a <form>")), w.toString)
  }

  test("dynamic name under use:form warns to use the hand-written spread") {
    val w = warnings("""<form use:form={form}><input name={dyn}/></form>""")
    assert(w.exists(m => m.contains("dynamic name") && m.contains("form.field")), w.toString)
  }

  test("missing name under use:form warns that nothing is bound") {
    val w = warnings("""<form use:form={form}><input type="text"/></form>""")
    assert(w.exists(_.contains("no name attribute")), w.toString)
  }

  test("static name under use:form does not warn") {
    val w = warnings("""<form use:form={form}><input name="email" type="text"/></form>""")
    assert(!w.exists(_.contains("use:form")), w.toString)
    assert(!w.exists(_.contains("no name")), w.toString)
  }

  test("data-form-ignore suppresses the missing-name warning") {
    val w = warnings("""<form use:form={form}><input type="text" data-form-ignore/></form>""")
    assert(!w.exists(_.contains("no name")), w.toString)
  }

  test("submit/button inputs are not warned about (nothing to bind)") {
    val w = warnings("""<form use:form={form}><input type="submit" value="Go"/></form>""")
    assert(!w.exists(_.contains("no name")), w.toString)
  }

  test("controls outside any use:form are not checked") {
    val w = warnings("""<form><input type="text"/></form>""")
    assert(!w.exists(_.contains("no name")), w.toString)
  }

  test("select and textarea without a name warn under use:form") {
    val sel = warnings("""<form use:form={form}><select></select></form>""")
    assert(sel.exists(_.contains("no name")), sel.toString)
    val ta = warnings("""<form use:form={form}><textarea></textarea></form>""")
    assert(ta.exists(_.contains("no name")), ta.toString)
  }
