/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.given

import org.scalajs.dom

class FormTestKitSpec extends munit.FunSuite:

  test("EnhanceResult builds the success/failure/redirect envelope shapes"):
    assert(EnhanceResult.success("""{"x":1}""").contains(""""type":"success""""))
    assert(EnhanceResult.failure(422, """{"errors":["bad"]}""").contains(""""status":422"""))
    assertEquals(EnhanceResult.redirect("/dashboard"), """{"type":"redirect","status":303,"location":"/dashboard"}""")

  test("FetchStub installs window.fetch that records the request and returns the body"):
    val window   = dom.window.asInstanceOf[js.Dynamic]
    val original = window.selectDynamic("fetch")
    val stub     = FetchStub.install(status = 200, body = "hello")
    assert(window.selectDynamic("fetch") ne original, "fetch was not installed")

    val init = new dom.RequestInit {}
    init.method = dom.HttpMethod.POST
    init.body   = "a=1"
    // invoke through the window object (what `dom.fetch` resolves to in a browser)
    window
      .applyDynamic("fetch")("/submit", init)
      .asInstanceOf[js.Promise[dom.Response]]
      .toFuture
      .flatMap(_.text().toFuture)
      .map { text =>
        assertEquals(text, "hello")
        assertEquals(stub.lastCall.map(_.url), Some("/submit"))
        assertEquals(stub.lastCall.map(_.method), Some("POST"))
        assertEquals(stub.lastCall.map(_.body), Some("a=1"))
        stub.restore()
        assert(window.selectDynamic("fetch") eq original, "fetch was not restored")
      }

  test("userEvent.submit dispatches a cancelable submit event carrying the submitter"):
    val container = dom.document.createElement("div")
    val form      = dom.document.createElement("form")
    val button    = dom.document.createElement("button")
    button.setAttribute("id", "go")
    form.appendChild(button)
    container.appendChild(form)

    var received: dom.Event = null
    form.addEventListener(
      "submit",
      (e: dom.Event) =>
        e.preventDefault()
        received = e
    )

    new UserEvent(container, () => false).submit("form", submitter = "#go")

    assert(received != null, "submit listener was not invoked")
    assertEquals(received.cancelable, true)
    assertEquals(received.asInstanceOf[js.Dynamic].submitter.asInstanceOf[dom.Element], button)
