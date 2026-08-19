/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package components

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import org.scalajs.dom

/** JS implementation: the real echarts, imported via `@JSImport`. Runs on the
  * client during hydration (`onMount`). Vite resolves the bare `"echarts"`
  * specifier from `node_modules` when bundling the hydration entry. */
object ChartHost:
  type Chart = EChartsInstance

  @js.native
  trait EChartsInstance extends js.Object:
    def setOption(option: js.Object): Unit = js.native

  @js.native
  @JSImport("echarts", JSImport.Namespace)
  object echarts extends js.Object:
    def init(el: dom.Element): EChartsInstance = js.native

  def render(elementId: String, categories: List[String], values: List[Int]): Chart =
    val chart = echarts.init(dom.document.getElementById(elementId))
    chart.setOption(option(categories, values))
    chart

  def update(chart: Chart, categories: List[String], values: List[Int]): Unit =
    chart.setOption(option(categories, values))

  private def option(categories: List[String], values: List[Int]): js.Object =
    js.Dynamic
      .literal(
        title   = js.Dynamic.literal(text = "Weekly Sales", left = "center"),
        tooltip = js.Dynamic.literal(),
        xAxis   = js.Dynamic.literal(`type` = "category", data = js.Array(categories*)),
        yAxis   = js.Dynamic.literal(`type` = "value"),
        series = js.Array(
          js.Dynamic.literal(
            `type`    = "bar",
            data      = js.Array(values.map(v => (v: js.Any))*),
            itemStyle = js.Dynamic.literal(color = "#c03050")
          )
        )
      )
      .asInstanceOf[js.Object]
