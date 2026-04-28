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
    *  - `List(Wildcard)` matches any path regardless of depth.
    *  - Otherwise `pattern` and `actual` must have the same length, and each
    *    pair must match: `Static(s)` equals the literal segment; `Param` and
    *    `Wildcard` accept any value.
    */
  private[meltkit] def matches(pattern: List[PathSegment], actual: List[String]): Boolean =
    pattern match
      case List(Wildcard) => true
      case _              =>
        pattern.length == actual.length &&
        pattern.zip(actual).forall {
          case (Static(s), seg) => s == seg
          case (Param(_), _)    => true
          case (Wildcard, _)    => true
        }
