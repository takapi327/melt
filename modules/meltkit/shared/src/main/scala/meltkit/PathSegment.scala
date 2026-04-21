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
