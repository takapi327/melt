/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltkit

/** A single segment in a URL path pattern. */
enum PathSegment:
  /** A fixed string segment (e.g., `"users"` in `/users/:id`). */
  case Static(value: String)

  /** A dynamic segment bound to a named path parameter. */
  case Param(name: String)

  /** A wildcard that matches any remaining path.
    *
    * When used as the sole segment (`List(Wildcard)`), the route matches
    * every incoming request regardless of depth. Register catch-all routes
    * last so that more-specific routes take precedence via `find`.
    */
  case Wildcard

object PathSegment:

  /** Returns `true` when `pattern` matches `actual`.
    *
    * Rules:
    *  - A trailing `Wildcard` matches the rest of the path, however deep, including
    *    nothing at all. `List(Wildcard)` is the case where nothing precedes it, so a
    *    catch-all matches every path; mounted under `admin` it becomes
    *    `List(Static("admin"), Wildcard)` and matches `/admin` and everything below it.
    *    A catch-all that stopped at the mount's first level would leave the rest of the
    *    subtree unrouted — and, since hooks are scoped to the same patterns, unguarded.
    *  - Otherwise `pattern` and `actual` must have the same length, and each pair must
    *    match: `Static(s)` equals the literal segment; `Param` and `Wildcard` accept any
    *    single value.
    */
  private[meltkit] def matches(pattern: List[PathSegment], actual: List[String]): Boolean =
    pattern.lastOption match
      case Some(Wildcard) =>
        val fixed = pattern.init
        fixed.length <= actual.length && eachMatches(fixed, actual)
      case _ =>
        pattern.length == actual.length && eachMatches(pattern, actual)

  /** Returns `true` when `area` is `actual` or an ancestor of it.
    *
    * Used for the area a mounted router's hooks guard. Unlike [[matches]] this is about
    * containment, not routing: the empty area covers every path, and a request deeper than
    * the area is still inside it.
    */
  private[meltkit] def covers(area: List[PathSegment], actual: List[String]): Boolean =
    area.length <= actual.length && eachMatches(area, actual)

  private def eachMatches(pattern: List[PathSegment], actual: List[String]): Boolean =
    pattern.zip(actual).forall {
      case (Static(s), seg) => s == seg
      case (Param(_), _)    => true
      case (Wildcard, _)    => true
    }
