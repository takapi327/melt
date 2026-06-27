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
       |  val count   = State(0)
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

  val todoMelt: String =
    """|<!-- TodoList.melt -->
       |<script lang="scala">
       |  case class Todo(id: Int, text: String, done: Boolean)
       |
       |  val todos = State(List(
       |    Todo(1, "Learn Melt", false),
       |    Todo(2, "Build something", false)
       |  ))
       |  val input = State("")
       |
       |  def add(): Unit =
       |    if input.value.trim.nonEmpty then
       |      val next = Todo(todos.value.length + 1, input.value.trim, false)
       |      todos.update(_ :+ next)
       |      input.set("")
       |
       |  def toggle(id: Int): Unit =
       |    todos.update(_.map(t => if t.id == id then t.copy(done = !t.done) else t))
       |</script>
       |
       |<div class="todos">
       |  <div class="add-row">
       |    <input type="text" bind:value={input} placeholder="New task..." />
       |    <button onclick={_ => add()}>Add</button>
       |  </div>
       |  {todos.value.map((t: Todo) =>
       |    <label class:done={t.done}>
       |      <input type="checkbox" onclick={_ => toggle(t.id)} />
       |      {t.text}
       |    </label>
       |  )}
       |</div>
       |
       |<style>
       |  .todos    { display: flex; flex-direction: column; gap: 8px; }
       |  .add-row  { display: flex; gap: 8px; }
       |  label     { display: flex; gap: 8px; align-items: center; cursor: pointer; }
       |  label.done { opacity: 0.5; text-decoration: line-through; }
       |</style>""".stripMargin

  val helloMelt: String =
    """|<!-- Hello.melt -->
       |<script lang="scala">
       |  case class Props(name: String = "World")
       |</script>
       |
       |<p>Hello, {props.name}!</p>""".stripMargin

  case class Sample(label: String, code: String)

  val samples: List[Sample] = List(
    Sample("Counter", counterMelt),
    Sample("Todo List", todoMelt),
    Sample("Hello", helloMelt)
  )
