/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** Runs a side-effect whenever the dependency changes.
  *
  * The effect body runs immediately with the current value, then re-runs
  * on each subsequent change. Any `onCleanup` registered inside the effect
  * body is executed before re-execution (and on component destruction).
  *
  * {{{
  * val count = Var(0)
  * effect(count) { n =>
  *   val timer = managed(
  *     dom.window.setInterval(() => println(n), 1000),
  *     id => dom.window.clearInterval(id)
  *   )
  * }
  * }}}
  */
def effect[A](dep: Var[A])(f: A => Unit): Unit =
  var innerCleanups: List[() => Unit] = Nil

  def run(value: A): Unit =
    Cleanup.runAll(innerCleanups)
    Cleanup.pushScope()
    f(value)
    innerCleanups = Cleanup.popScope()

  run(dep.now())
  val cancel = dep.subscribe(run)
  Cleanup.register(() => { cancel(); Cleanup.runAll(innerCleanups) })

def effect[A](dep: Signal[A])(f: A => Unit): Unit =
  var innerCleanups: List[() => Unit] = Nil

  def run(value: A): Unit =
    Cleanup.runAll(innerCleanups)
    Cleanup.pushScope()
    f(value)
    innerCleanups = Cleanup.popScope()

  run(dep.now())
  val cancel = dep.subscribe(run)
  Cleanup.register(() => { cancel(); Cleanup.runAll(innerCleanups) })

/** Two-dependency effect — re-runs when either dependency changes. */
def effect[A, B](depA: Var[A], depB: Var[B])(f: (A, B) => Unit): Unit =
  var innerCleanups: List[() => Unit] = Nil

  def run(): Unit =
    Cleanup.runAll(innerCleanups)
    Cleanup.pushScope()
    f(depA.now(), depB.now())
    innerCleanups = Cleanup.popScope()

  run()
  val cancelA = depA.subscribe(_ => run())
  val cancelB = depB.subscribe(_ => run())
  Cleanup.register(() => { cancelA(); cancelB(); Cleanup.runAll(innerCleanups) })
