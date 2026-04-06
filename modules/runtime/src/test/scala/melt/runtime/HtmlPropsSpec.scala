/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

class HtmlPropsSpec extends munit.FunSuite:

  // ── HtmlAttrs ─────────────────────────────────────────────────────────

  test("HtmlAttrs.empty has no entries") {
    assertEquals(HtmlAttrs.empty.entries, Map.empty[String, String])
  }

  test("HtmlAttrs varargs constructor") {
    val attrs = HtmlAttrs("id" -> "main", "class" -> "container")
    assertEquals(attrs.entries, Map("id" -> "main", "class" -> "container"))
  }

  // ── HtmlProps base trait ──────────────────────────────────────────────

  test("HtmlProps global attributes default to None") {
    val props = new HtmlProps {}
    assertEquals(props.id, None)
    assertEquals(props.title, None)
    assertEquals(props.hidden, None)
    assertEquals(props.tabindex, None)
    assertEquals(props.role, None)
  }

  test("HtmlProps allHtmlAttrs collects set fields") {
    val props = new HtmlProps {}
    props.id = Some("test")
    props.role = Some("button")
    props.tabindex = Some(0)
    val attrs = props.allHtmlAttrs
    assertEquals(attrs.entries("id"), "test")
    assertEquals(attrs.entries("role"), "button")
    assertEquals(attrs.entries("tabindex"), "0")
    assert(!attrs.entries.contains("title"))
  }

  test("HtmlProps allHtmlAttrs includes custom HtmlAttrs") {
    val props = new HtmlProps {}
    props.id = Some("el")
    props.withHtml(HtmlAttrs("data-x" -> "1", "aria-label" -> "hello"))
    val attrs = props.allHtmlAttrs
    assertEquals(attrs.entries("id"), "el")
    assertEquals(attrs.entries("data-x"), "1")
    assertEquals(attrs.entries("aria-label"), "hello")
  }

  test("HtmlProps hidden=true emits empty string attribute") {
    val props = new HtmlProps {}
    props.hidden = Some(true)
    val attrs = props.allHtmlAttrs
    assertEquals(attrs.entries("hidden"), "")
  }

  test("HtmlProps hidden=false is not included") {
    val props = new HtmlProps {}
    props.hidden = Some(false)
    val attrs = props.allHtmlAttrs
    assert(!attrs.entries.contains("hidden"))
  }

  // ── ButtonHtmlProps ───────────────────────────────────────────────────

  test("ButtonHtmlProps has button-specific fields") {
    val props = new ButtonHtmlProps {}
    props.disabled = Some(true)
    props.buttonType = Some("submit")
    props.name = Some("btn")
    assertEquals(props.disabled, Some(true))
    assertEquals(props.buttonType, Some("submit"))
    assertEquals(props.name, Some("btn"))
  }

  test("ButtonHtmlProps inherits global attributes") {
    val props = new ButtonHtmlProps {}
    props.id = Some("my-btn")
    props.disabled = Some(true)
    val attrs = props.allHtmlAttrs
    assertEquals(attrs.entries("id"), "my-btn")
  }

  // ── InputHtmlProps ────────────────────────────────────────────────────

  test("InputHtmlProps has input-specific fields") {
    val props = new InputHtmlProps {}
    props.inputType = Some("email")
    props.placeholder = Some("Enter email")
    props.required = Some(true)
    props.maxLength = Some(100)
    props.pattern = Some("[a-z]+")
    assertEquals(props.inputType, Some("email"))
    assertEquals(props.placeholder, Some("Enter email"))
    assertEquals(props.required, Some(true))
    assertEquals(props.maxLength, Some(100))
    assertEquals(props.pattern, Some("[a-z]+"))
  }

  test("InputHtmlProps numeric fields") {
    val props = new InputHtmlProps {}
    props.inputType = Some("number")
    props.min = Some("0")
    props.max = Some("100")
    props.step = Some("0.1")
    assertEquals(props.min, Some("0"))
    assertEquals(props.max, Some("100"))
    assertEquals(props.step, Some("0.1"))
  }

  // ── AnchorHtmlProps ───────────────────────────────────────────────────

  test("AnchorHtmlProps has anchor-specific fields") {
    val props = new AnchorHtmlProps {}
    props.href = Some("https://example.com")
    props.target = Some("_blank")
    props.rel = Some("noopener noreferrer")
    props.download = Some("file.pdf")
    assertEquals(props.href, Some("https://example.com"))
    assertEquals(props.target, Some("_blank"))
    assertEquals(props.rel, Some("noopener noreferrer"))
    assertEquals(props.download, Some("file.pdf"))
  }

  // ── FormHtmlProps ─────────────────────────────────────────────────────

  test("FormHtmlProps has form-specific fields") {
    val props = new FormHtmlProps {}
    props.action = Some("/submit")
    props.method = Some("POST")
    props.enctype = Some("multipart/form-data")
    props.novalidate = Some(true)
    assertEquals(props.action, Some("/submit"))
    assertEquals(props.method, Some("POST"))
    assertEquals(props.novalidate, Some(true))
  }

  // ── ImgHtmlProps ──────────────────────────────────────────────────────

  test("ImgHtmlProps has img-specific fields") {
    val props = new ImgHtmlProps {}
    props.src = Some("photo.jpg")
    props.alt = Some("A photo")
    props.width = Some(640)
    props.height = Some(480)
    props.loading = Some("lazy")
    assertEquals(props.src, Some("photo.jpg"))
    assertEquals(props.alt, Some("A photo"))
    assertEquals(props.width, Some(640))
    assertEquals(props.height, Some(480))
    assertEquals(props.loading, Some("lazy"))
  }

  // ── SelectHtmlProps ───────────────────────────────────────────────────

  test("SelectHtmlProps has select-specific fields") {
    val props = new SelectHtmlProps {}
    props.multiple = Some(true)
    props.size = Some(5)
    props.required = Some(true)
    assertEquals(props.multiple, Some(true))
    assertEquals(props.size, Some(5))
    assertEquals(props.required, Some(true))
  }

  // ── TextAreaHtmlProps ─────────────────────────────────────────────────

  test("TextAreaHtmlProps has textarea-specific fields") {
    val props = new TextAreaHtmlProps {}
    props.rows = Some(10)
    props.cols = Some(40)
    props.placeholder = Some("Enter text...")
    props.maxLength = Some(500)
    props.wrap = Some("soft")
    props.readonly = Some(true)
    assertEquals(props.rows, Some(10))
    assertEquals(props.cols, Some(40))
    assertEquals(props.placeholder, Some("Enter text..."))
    assertEquals(props.maxLength, Some(500))
    assertEquals(props.wrap, Some("soft"))
    assertEquals(props.readonly, Some(true))
  }

  // ── withHtml chaining ─────────────────────────────────────────────────

  test("withHtml returns this for fluent chaining") {
    val props = new InputHtmlProps {}
    props.inputType = Some("text")
    val result = props.withHtml(HtmlAttrs("data-id" -> "123"))
    assert(result eq props, "withHtml should return the same instance")
    assertEquals(props.html.entries("data-id"), "123")
  }

  // ── allHtmlAttrs entries — full pipeline (fields → attrs map) ───────────
  // Note: DOM element tests require jsdom. Here we verify the generated
  // HtmlAttrs entries map is correct; DOM application is tested via
  // ScalaCodeGenSpec (compiled .melt → fastLinkJS integration).

  test("ButtonHtmlProps allHtmlAttrs collects global + element-specific + custom entries") {
    val props = new ButtonHtmlProps {}
    props.id = Some("btn-1")
    props.role = Some("menuitem")
    props.disabled = Some(true)
    props.buttonType = Some("submit")
    props.withHtml(HtmlAttrs("aria-label" -> "Submit form"))
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("id"), "btn-1")
    assertEquals(entries("role"), "menuitem")
    assert(entries.contains("disabled"), "disabled should be in allHtmlAttrs")
    assertEquals(entries("type"), "submit")
    assertEquals(entries("aria-label"), "Submit form")
  }

  test("InputHtmlProps allHtmlAttrs collects global + element-specific entries") {
    val props = new InputHtmlProps {}
    props.id = Some("email-input")
    props.tabindex = Some(1)
    props.inputType = Some("email")
    props.placeholder = Some("Enter email")
    props.required = Some(true)
    props.maxLength = Some(100)
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("id"), "email-input")
    assertEquals(entries("tabindex"), "1")
    assertEquals(entries("type"), "email")
    assertEquals(entries("placeholder"), "Enter email")
    assert(entries.contains("required"), "required should be in allHtmlAttrs")
    assertEquals(entries("maxlength"), "100")
  }

  test("AnchorHtmlProps allHtmlAttrs collects href, target, rel") {
    val props = new AnchorHtmlProps {}
    props.id = Some("link")
    props.withHtml(HtmlAttrs("href" -> "https://example.com", "target" -> "_blank", "rel" -> "noopener"))
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("href"), "https://example.com")
    assertEquals(entries("target"), "_blank")
    assertEquals(entries("rel"), "noopener")
    assertEquals(entries("id"), "link")
  }

  test("ImgHtmlProps allHtmlAttrs collects src, alt, loading") {
    val props = new ImgHtmlProps {}
    props.title = Some("Photo")
    props.withHtml(HtmlAttrs("src" -> "photo.jpg", "alt" -> "A photo", "loading" -> "lazy"))
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("src"), "photo.jpg")
    assertEquals(entries("alt"), "A photo")
    assertEquals(entries("loading"), "lazy")
    assertEquals(entries("title"), "Photo")
  }

  test("FormHtmlProps allHtmlAttrs collects action, method") {
    val props = new FormHtmlProps {}
    props.id = Some("login")
    props.withHtml(HtmlAttrs("action" -> "/login", "method" -> "POST"))
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("action"), "/login")
    assertEquals(entries("method"), "POST")
    assertEquals(entries("id"), "login")
  }

  test("TextAreaHtmlProps allHtmlAttrs collects rows, cols, placeholder") {
    val props = new TextAreaHtmlProps {}
    props.id = Some("msg")
    props.withHtml(HtmlAttrs("rows" -> "5", "cols" -> "40", "placeholder" -> "Write..."))
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("rows"), "5")
    assertEquals(entries("cols"), "40")
    assertEquals(entries("placeholder"), "Write...")
    assertEquals(entries("id"), "msg")
  }

  test("SelectHtmlProps allHtmlAttrs collects multiple") {
    val props = new SelectHtmlProps {}
    props.id = Some("sel")
    props.withHtml(HtmlAttrs("multiple" -> ""))
    val entries = props.allHtmlAttrs.entries
    assertEquals(entries("id"), "sel")
    assert(entries.contains("multiple"))
  }
