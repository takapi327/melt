/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import melt.runtime.forms.Form
import melt.runtime.json.PropsCodec
import melt.runtime.Async

import meltkit.*

class FormInvalidationSpec extends munit.FunSuite:

  case class DraftForm(title: String) derives PropsCodec

  /** Builds a Query whose refresh records that it was invalidated. */
  private def probeQuery(onRefresh: () => Unit): Query[Int] =
    new Query[Int]("q", "null", summon[PropsCodec[Int]], Async.Loading, _ => onRefresh())

  test("invalidates refreshes the declared queries when the form succeeds"):
    var refreshed = 0
    val q         = probeQuery(() => refreshed += 1)
    val form      = Form(DraftForm("")).invalidates(q)

    form.afterResult("failure", () => ())
    assertEquals(refreshed, 0, "no refresh on a failed submit")

    form.afterResult("success", () => ())
    assertEquals(refreshed, 1, "the query refreshes on success")

  test("invalidates refreshes every declared query"):
    var a    = 0
    var b    = 0
    val form = Form(DraftForm("")).invalidates(probeQuery(() => a += 1), probeQuery(() => b += 1))
    form.afterResult("success", () => ())
    assertEquals((a, b), (1, 1))
