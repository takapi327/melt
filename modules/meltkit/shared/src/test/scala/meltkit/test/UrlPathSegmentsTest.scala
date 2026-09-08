/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit.test

import meltkit.Url

/** [[Url.pathSegments]] — the one path split the Undertow and Node bindings share.
  *
  * Both feed it to route matching *and* to `RequestEvent.pathSegments`. A prefix-scoped hook
  * compares against the latter, so if the two ever came from separate expressions a guard
  * could skip a request the router accepted. Testing the shared source covers both bindings
  * on both platforms without standing a server up.
  */
class UrlPathSegmentsTest extends munit.FunSuite:

  private def segments(raw: String): List[String] =
    Url.parse(raw, "http://localhost:8080").pathSegments

  test("a plain path splits into its segments"):
    assertEquals(segments("/admin/users"), List("admin", "users"))

  test("the root path has no segments"):
    assertEquals(segments("/"), Nil)

  test("a trailing slash adds no empty segment"):
    assertEquals(segments("/admin/users/"), List("admin", "users"))

  test("repeated slashes collapse"):
    assertEquals(segments("//admin//users"), List("admin", "users"))

  test("a query string is not part of the path"):
    assertEquals(segments("/admin/users?limit=1"), List("admin", "users"))

  test("segments carry whatever encoding pathname holds"):
    assertNotEquals(segments("/%61dmin/users").head, "admin")
