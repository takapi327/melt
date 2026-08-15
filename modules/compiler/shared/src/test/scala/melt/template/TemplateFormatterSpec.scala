/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.template

import melt.ast.TemplateNode
import melt.parser.TemplateParser

class TemplateFormatterSpec extends munit.FunSuite:

  private def parse(src: String): List[TemplateNode] =
    TemplateParser.parseWithWarnings(src)._1

  private def fmt(src: String): String =
    val (nodes, positions, _) = TemplateParser.parseWithWarnings(src)
    TemplateFormatter.format(nodes, positions, src)

  /** Golden check + the semantic-equivalence valve + idempotency. */
  private def check(name: String)(input: String, expected: String): Unit =
    test(name) {
      val out = fmt(input)
      assertNoDiff(out, expected)
      // Valve: rendering is a pure function of the AST → same AST ⇒ same render.
      assertEquals(parse(out), parse(input), "valve: parse(format(x)) != parse(x)")
      // Idempotency.
      assertNoDiff(fmt(out), expected)
    }

  // ── Golden fixtures ─────────────────────────────────────────────────────────

  check("text element stays inline")(
    """<div class="a">hi</div>""",
    """<div class="a">hi</div>"""
  )

  check("nested block: whitespace-separated children each on their own line")(
    "<ul>\n  <li>a</li>\n  <li>b</li>\n</ul>",
    """<ul>
      |  <li>a</li>
      |  <li>b</li>
      |</ul>""".stripMargin
  )

  check("block siblings each on their own line (inter-sibling whitespace is not in the AST)")(
    "<div><span></span><b></b></div>",
    """<div>
      |  <span></span>
      |  <b></b>
      |</div>""".stripMargin
  )

  check("single block child expands (boundary whitespace is trimmed)")(
    "<div><span>x</span></div>",
    """<div>
      |  <span>x</span>
      |</div>""".stripMargin
  )

  check("expression is kept verbatim inline")(
    "<p>{count}</p>",
    "<p>{count}</p>"
  )

  check("attributes normalise to double quotes; void self-closes")(
    "<input type='text' disabled name={n}/>",
    """<input type="text" disabled name={n} />"""
  )

  check("event handler and directive round-trip")(
    "<button onclick={h} class:active={f}>x</button>",
    "<button onclick={h} class:active={f}>x</button>"
  )

  check("InlineTemplate (map) kept verbatim")(
    "<ul>{items.map(i => <li>{i}</li>)}</ul>",
    "<ul>{items.map(i => <li>{i}</li>)}</ul>"
  )

  check("ampersand re-escaped")(
    "<p>a &amp; b</p>",
    "<p>a &amp; b</p>"
  )

  check("brace entities re-escaped in text")(
    "<p>&lbrace;x&rbrace;</p>",
    "<p>&lbrace;x&rbrace;</p>"
  )

  check("void element")(
    "<br>",
    "<br />"
  )

  check("melt:head with reactive title")(
    "<melt:head>\n  <title>{t}</title>\n</melt:head>",
    """<melt:head>
      |  <title>{t}</title>
      |</melt:head>""".stripMargin
  )

  check("melt:element (dynamic tag)")(
    """<melt:element this={tag} class="a">x</melt:element>""",
    """<melt:element this={tag} class="a">x</melt:element>"""
  )

  check("component with props (empty → explicit close)")(
    "<Counter count={n} />",
    "<Counter count={n}></Counter>"
  )

  check("spread and shorthand attributes")(
    "<Item {...props} {label} />",
    "<Item {...props} {label}></Item>"
  )

  check("top-level siblings")(
    "<h1>t</h1>\n<p>b</p>",
    """<h1>t</h1>
      |<p>b</p>""".stripMargin
  )

  check("render call")(
    "<div>{@render row(x)}</div>",
    "<div>{@render row(x)}</div>"
  )

  check("HTML comment preserved (block context → own line)")(
    "<div><!-- hero --><span>x</span></div>",
    """<div>
      |  <!-- hero -->
      |  <span>x</span>
      |</div>""".stripMargin
  )

  check("top-level comment kept before a section")(
    "<!-- Hero --><section>{x}</section>",
    """<!-- Hero -->
      |<section>{x}</section>""".stripMargin
  )

  // ── Valve-only (structure preserved on messier input) ───────────────────────

  test("valve holds on a compound template") {
    val src =
      "<section>\n" +
        "  <h2>{title}</h2>\n" +
        "  <ul>{items.map(i => <li class=\"row\">{i.name}</li>)}</ul>\n" +
        "  <button onclick={add}>Add</button>\n" +
        "</section>"
    assertEquals(parse(fmt(src)), parse(src))
    assertNoDiff(fmt(fmt(src)), fmt(src)) // idempotent
  }

  // ── Deferred nodes raise (so the caller can skip, never corrupt) ─────────────

  test("boundary is reported unsupported (caller keeps original)") {
    val src = "<melt:boundary onerror={h}><p>x</p></melt:boundary>"
    val (nodes, positions, _) = TemplateParser.parseWithWarnings(src)
    intercept[TemplateFormatUnsupported](TemplateFormatter.format(nodes, positions, src))
  }
