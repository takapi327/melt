/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

// ── effect ─────────────────────────────────────────────────────────────────

/** Runs a side-effect **after** DOM updates whenever the dependency changes.
  *
  * The effect body runs immediately with the current value (before the first
  * DOM paint), then re-runs in the **Post** phase — after [[Bind]] DOM mutations
  * — on each subsequent change.  Any `onCleanup` registered inside the effect
  * body is executed before re-execution and on component destruction.
  *
  * {{{
  * val count = Var(0)
  * effect(count) { n =>
  *   dom.console.log(s"count is now: $$n")  // DOM is already updated at this point
  * }
  * }}}
  */
def effect[A](dep: Var[A])(f: A => Unit): Unit =
  var innerNode: Option[OwnerNode] = None

  def run(value: A): Unit =
    innerNode.foreach(_.destroy())
    val (_, node) = Owner.withNew { f(value) }
    innerNode = Some(node)

  run(dep.now())
  val cancel = dep.subscribePost(run)
  Cleanup.register(() => { cancel(); innerNode.foreach(_.destroy()) })

def effect[A](dep: Signal[A])(f: A => Unit): Unit =
  var innerNode: Option[OwnerNode] = None

  def run(value: A): Unit =
    innerNode.foreach(_.destroy())
    val (_, node) = Owner.withNew { f(value) }
    innerNode = Some(node)

  run(dep.now())
  val cancel = dep.subscribePost(run)
  Cleanup.register(() => { cancel(); innerNode.foreach(_.destroy()) })

/** Two-dependency effect — re-runs in the **Post** phase when either dependency changes.
  *
  * Uses a scheduled run pattern so that if both change inside a `batch`,
  * the effect body runs only once.
  */
def effect[A, B](depA: Var[A], depB: Var[B])(f: (A, B) => Unit): Unit =
  var innerNode: Option[OwnerNode] = None

  def run(): Unit =
    innerNode.foreach(_.destroy())
    val (_, node) = Owner.withNew { f(depA.now(), depB.now()) }
    innerNode = Some(node)

  lazy val scheduleRun: () => Unit = () => run()

  def trigger(): Unit =
    if Batch.isBatching || Batch.isFlushing then Batch.enqueue(scheduleRun)
    else run()

  run()
  val cancelA = depA.subscribePost(_ => trigger())
  val cancelB = depB.subscribePost(_ => trigger())
  Cleanup.register(() => { cancelA(); cancelB(); innerNode.foreach(_.destroy()) })

// ── layoutEffect ────────────────────────────────────────────────────────────

/** Runs a side-effect **before** DOM updates whenever the dependency changes.
  *
  * Executes in the **Pre** phase — before [[Bind]] DOM mutations — allowing
  * you to read the current DOM state (e.g. scroll position, element size)
  * before the update is applied.
  *
  * Unlike [[effect]], `layoutEffect` does **not** run on initial creation;
  * it only fires on subsequent changes to `dep`.
  *
  * {{{
  * val messages = Var(List.empty[String])
  * val listRef  = Ref.empty[dom.Element]
  * var atBottom = false
  *
  * layoutEffect(messages) { _ =>
  *   // DOM has NOT been updated yet — read pre-update state here
  *   listRef.foreach { el =>
  *     atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight
  *   }
  * }
  *
  * effect(messages) { _ =>
  *   // DOM IS updated — act on post-update state here
  *   if atBottom then listRef.foreach(el => el.scrollTop = el.scrollHeight)
  * }
  * }}}
  */
def layoutEffect[A](dep: Var[A])(f: A => Unit): Unit =
  val cancel = dep.subscribePre(f)
  Cleanup.register(cancel)

def layoutEffect[A](dep: Signal[A])(f: A => Unit): Unit =
  val cancel = dep.subscribePre(f)
  Cleanup.register(cancel)

/** Two-dependency `layoutEffect` — fires in the **Pre** phase when either dependency changes.
  *
  * Uses a scheduled run pattern to run at most once per `batch`.
  */
def layoutEffect[A, B](depA: Var[A], depB: Var[B])(f: (A, B) => Unit): Unit =
  lazy val scheduleRun: () => Unit = () => f(depA.now(), depB.now())

  def trigger(): Unit =
    if Batch.isBatching || Batch.isFlushing then Batch.enqueue(scheduleRun)
    else f(depA.now(), depB.now())

  val cancelA = depA.subscribePre(_ => trigger())
  val cancelB = depB.subscribePre(_ => trigger())
  Cleanup.register(() => { cancelA(); cancelB(); () })
