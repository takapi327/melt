/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

/** JVM (SSR) stub. echarts is browser-only, so on the server every operation is
  * a no-op — `onMount` never runs during SSR, but the shared component must still
  * compile against this object. The JS counterpart holds the real `@JSImport`. */
object ChartHost:
  type Chart = Unit
  def render(elementId: String, categories: List[String], values: List[Int]): Chart = ()
  def update(chart: Chart, categories: List[String], values: List[Int]): Unit        = ()
