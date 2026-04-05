/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

import org.scalajs.dom
import components.App

object Main:
  def main(args: Array[String]): Unit =
    val target = dom.document.getElementById("app")
    App.mount(target)
