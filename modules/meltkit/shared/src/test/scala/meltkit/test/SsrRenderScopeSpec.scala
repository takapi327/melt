/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import melt.runtime.json.PropsCodec
import melt.runtime.render.RenderResult
import melt.runtime.Async

import meltkit.*

class SsrRenderScopeSpec extends munit.FunSuite:

  private def query(name: String, args: String): Query[Int] =
    new Query[Int](name, args, summon[PropsCodec[Int]], Async.Loading, _ => ())

  /** A branch renderer that stamps the settled state into a tiny fragment. */
  private val branch: Async[Int] => RenderResult =
    case Async.Loading   => RenderResult("<loading/>", "")
    case Async.Done(x)   => RenderResult(s"<done>$x</done>", "")
    case Async.Failed(e) => RenderResult(s"<err>${ e.getMessage }</err>", "")

  test("resolveAll renders the Done branch from the resolveQuery result"):
    val scope = new SsrRenderScope[Future]((_, _) => Future.successful(Some("42")))
    scope.suspend("m1", query("posts.count", "null"), branch)
    scope.resolveAll.map(r => assertEquals(r.fragments("m1").body, "<done>42</done>"))

  test("a failing query resolves to the Failed branch, not the whole page"):
    val scope = new SsrRenderScope[Future]((_, _) => Future.failed(new RuntimeException("db down")))
    scope.suspend("m1", query("q", "null"), branch)
    scope.resolveAll.map(r => assertEquals(r.fragments("m1").body, "<err>db down</err>"))

  test("an unregistered query keeps the Loading fallback"):
    val scope = new SsrRenderScope[Future]((_, _) => Future.successful(None))
    scope.suspend("m1", query("missing", "null"), branch)
    scope.resolveAll.map(r => assertEquals(r.fragments("m1").body, "<loading/>"))

  test("boundaries resolve independently and are keyed by marker id"):
    val scope = new SsrRenderScope[Future]((name, _) =>
      if name == "ok" then Future.successful(Some("1")) else Future.failed(new RuntimeException("x"))
    )
    scope.suspend("a", query("ok", "null"), branch)
    scope.suspend("b", query("bad", "null"), branch)
    scope.resolveAll.map { r =>
      assertEquals(r.fragments("a").body, "<done>1</done>")
      assertEquals(r.fragments("b").body, "<err>x</err>")
    }

  test("resolveAll seeds successfully resolved queries by key, but not failures"):
    val scope = new SsrRenderScope[Future]((name, _) =>
      if name == "ok" then Future.successful(Some("7")) else Future.failed(new RuntimeException("x"))
    )
    scope.suspend("a", query("ok", "null"), branch)
    scope.suspend("b", query("bad", "null"), branch)
    scope.resolveAll.map { r =>
      // key is `name\nargs`; only the resolved query is seeded (raw JSON verbatim)
      assert(r.seedJson.contains("\"ok\\nnull\":7"), r.seedJson)
      assert(!r.seedJson.contains("bad"), r.seedJson)
    }

  test("resolveAll with no seeds yields an empty seed JSON"):
    val scope = new SsrRenderScope[Future]((_, _) => Future.successful(None))
    scope.suspend("m1", query("missing", "null"), branch)
    scope.resolveAll.map(r => assertEquals(r.seedJson, ""))

  test("withScope exposes the current scope during the body and clears it after"):
    assertEquals(SsrRenderScope.current, None)
    val (seen, scope) = SsrRenderScope.withScope[Future, Boolean]((_, _) => Future.successful(None)) {
      SsrRenderScope.current.isDefined
    }
    assert(seen, "scope must be visible inside the body")
    assertEquals(SsrRenderScope.current, None, "scope must be cleared after")
    assert(!scope.nonEmpty)

  test("nextId allocates request-unique ids"):
    val scope = new SsrRenderScope[Future]((_, _) => Future.successful(None))
    assertNotEquals(scope.nextId(), scope.nextId())
