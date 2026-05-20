/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

object CodeSamples:

  val counterMelt: String =
    """|<!-- Counter.melt -->
       |<script lang="scala">
       |  val count   = Var(0)
       |  val doubled = count.map(_ * 2)
       |</script>
       |
       |<div class="counter">
       |  <h1>{count}</h1>
       |  <p>Doubled: {doubled}</p>
       |  <button onclick={_ => count += 1}>+1</button>
       |  <button onclick={_ => count -= 1}>-1</button>
       |  <button onclick={_ => count.set(0)}>Reset</button>
       |</div>
       |
       |<style>
       |  h1     { font-size: 4rem; color: #d6526a; }
       |  button { padding: 0.5rem 1rem; }
       |</style>""".stripMargin
