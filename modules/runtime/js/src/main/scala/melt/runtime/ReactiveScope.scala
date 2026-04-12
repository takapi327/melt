/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

/** A scoped reactive resource that pairs acquisition with cleanup.
  *
  * Inspired by Cats Effect's `Resource[F, A]`, `ReactiveScope[A]` describes
  * how to acquire a value of type `A` and how to release it. Multiple scopes
  * can be composed with `for`-comprehensions; cleanup always runs in **LIFO**
  * order regardless of exceptions.
  *
  * ==Lifetime modes==
  *
  *   - **Indefinite** (`allocated` / `allocate()`): caller controls when to
  *     stop. The returned cancel function **must** be called, otherwise
  *     subscriptions and side-effects persist forever.
  *   - **Bounded** (`use`): cleanup runs automatically when the body exits,
  *     even if it throws. Recommended for testing and short-lived work.
  *
  * ==Idempotency==
  *
  * Calling `allocated` or `allocate()` on the same instance more than once
  * returns the memoized result — `_run` is executed at most once per instance.
  * The returned cancel function is also idempotent: calling it multiple times
  * is safe and only the first call has any effect.
  *
  * {{{
  * val program: ReactiveScope[Unit] =
  *   for
  *     id <- ReactiveScope.make(setInterval(1000.0) { count += 1 })(clearInterval)
  *     _  <- ReactiveScope.effect(count) { n => dom.console.log(s"count: $$n") }
  *   yield ()
  *
  * // Indefinite lifetime — caller is responsible for calling cancel
  * val cancel: () => Unit = program.allocated
  * cancel()   // clearInterval → unsubscribe (LIFO)
  * cancel()   // second call is a no-op
  *
  * // Bounded lifetime — automatic cleanup after body exits
  * program.use { _ => runTests() }
  * }}}
  */
final class ReactiveScope[+A] private[runtime] (private val _run: () => (A, () => Unit)):

  /** Memoized result — `_run` is called at most once per instance.
    *
    * The cancel function wrapped here is idempotent via a `canceled` flag,
    * equivalent to Svelte 5's `DESTROYED` flag and Vue 3's `_active` flag.
    */
  private lazy val _memoized: (A, () => Unit) =
    val (a, cleanup) = _run()
    var canceled     = false
    val cancelFn: () => Unit = () =>
      if !canceled then
        canceled = true
        cleanup()
    (a, cancelFn)

  /** Transforms the acquired value without affecting cleanup behaviour. */
  def map[B](f: A => B): ReactiveScope[B] =
    ReactiveScope { () =>
      val (a, cancel) = _run()
      (f(a), cancel)
    }

  /** Sequences two scopes; the second may depend on the first's acquired value.
    *
    * Cleanup runs in reverse acquisition order (LIFO). If either cleanup
    * function throws, the exception is captured and the remaining cleanups
    * still execute. All captured exceptions are aggregated via `addSuppressed`
    * and the first one is re-thrown after all cleanups have run.
    *
    * If acquiring the second scope fails, the first scope is released
    * immediately before re-throwing.
    */
  def flatMap[B](f: A => ReactiveScope[B]): ReactiveScope[B] =
    ReactiveScope { () =>
      val (a, cleanupA) = _run()
      val (b, cleanupB) =
        try f(a)._run()
        catch
          case e: Throwable =>
            try cleanupA()
            catch case suppressed: Throwable => e.addSuppressed(suppressed)
            throw e

      val combinedCleanup: () => Unit = () =>
        var error: Option[Throwable] = None
        try cleanupB()
        catch case e: Throwable => error = Some(e)
        try cleanupA()
        catch
          case e: Throwable =>
            error match
              case Some(prev) => prev.addSuppressed(e)
              case None       => error = Some(e)
        error.foreach(throw _)

      (b, combinedCleanup)
    }

  /** Runs the scope and returns a cancel function (indefinite lifetime).
    *
    * The same instance always returns the same cancel function — `_run` is
    * called at most once. The cancel function itself is idempotent.
    *
    * **The returned function must be called** when the scope is no longer
    * needed. Omitting this call will cause subscriptions and other resources
    * to persist indefinitely. For bounded lifetimes prefer [[use]].
    *
    * The cancel function is also registered with the current [[Owner]] so that
    * the scope is automatically released when the enclosing component is destroyed.
    */
  def allocated: () => Unit =
    val cancel = _memoized._2
    Owner.register(cancel)
    cancel

  /** Runs the scope and returns both the acquired value and a cancel function.
    *
    * Same memoization and idempotency guarantees as [[allocated]].
    * The cancel function is registered with the current [[Owner]].
    */
  def allocate(): (A, () => Unit) =
    val result = _memoized
    Owner.register(result._2)
    result

  /** Runs the scope for the duration of `f`, then releases automatically.
    *
    * Cleanup is guaranteed even if `f` throws; the exception is re-thrown
    * after cleanup completes.
    *
    * {{{
    * scope.use { value =>
    *   doSomethingWith(value)
    * } // cleanup runs here
    * }}}
    */
  def use[B](f: A => B): B =
    val (a, cancel) = _memoized
    try f(a)
    finally cancel()

object ReactiveScope:

  private[runtime] def apply[A](run: () => (A, () => Unit)): ReactiveScope[A] =
    new ReactiveScope(run)

  // ── Constructors ────────────────────────────────────────────────────────

  /** A scope that immediately provides `a` with no cleanup. */
  def pure[A](a: A): ReactiveScope[A] =
    ReactiveScope(() => (a, () => ()))

  /** A scope that does nothing and requires no cleanup. */
  val unit: ReactiveScope[Unit] = pure(())

  /** Pairs an acquisition expression with its release function.
    *
    * Use when the release function needs the value produced by acquire.
    *
    * {{{
    * ReactiveScope.make(setInterval(1000.0) { tick() })(id => clearInterval(id))
    * ReactiveScope.make(openConnection())(_.close())
    * ReactiveScope.make(dep.subscribe(handler))(cancel => cancel())
    * }}}
    */
  def make[A](acquire: => A)(release: A => Unit): ReactiveScope[A] =
    ReactiveScope { () =>
      val a = acquire
      (a, () => release(a))
    }

  /** Pairs two independent side-effects as acquire and release.
    *
    * Use when acquire and release share no value — both are plain `Unit`
    * actions, typically closing over the same external reference.
    *
    * {{{
    * ReactiveScope.resource { ws.onmessage = handler }{ ws.onmessage = null }
    * }}}
    *
    * If acquire produces a value needed by release, use [[make]] instead:
    * {{{
    * ReactiveScope.make(dep.subscribe(handler))(cancel => cancel())
    * }}}
    *
    * Note: `release` is a by-name parameter evaluated at cleanup time, not
    * at the time `resource` is called.
    */
  def resource(acquire: => Unit)(release: => Unit): ReactiveScope[Unit] =
    ReactiveScope { () =>
      acquire
      ((), () => release)
    }

  // ── Reactive helpers ────────────────────────────────────────────────────

  /** Subscribes to a [[Var]] in the **Post** phase (after DOM updates).
    *
    * The body `f` runs immediately with the current value, then re-runs
    * whenever `dep` changes. Any [[onCleanup]] call inside `f` registers a
    * cleanup that runs before the next re-execution and on final release.
    *
    * {{{
    * ReactiveScope.effect(count) { n =>
    *   val observer = new MutationObserver(...)
    *   observer.observe(node)
    *   onCleanup(() => observer.disconnect())
    * }
    * }}}
    */
  def effect[A](dep: Var[A])(f: A => Unit): ReactiveScope[Unit] =
    ReactiveScope { () =>
      var innerNode: Option[OwnerNode] = None

      def run(value: A): Unit =
        innerNode.foreach(_.destroy())
        val (_, node) = Owner.withNew { f(value) }
        innerNode = Some(node)

      run(dep.now())
      val cancel = dep.subscribePost(run)
      ((), () => { cancel(); innerNode.foreach(_.destroy()) })
    }

  /** Subscribes to a [[Signal]] in the **Post** phase (after DOM updates).
    *
    * Behaves identically to the [[Var]] overload.
    */
  def effect[A](dep: Signal[A])(f: A => Unit): ReactiveScope[Unit] =
    ReactiveScope { () =>
      var innerNode: Option[OwnerNode] = None

      def run(value: A): Unit =
        innerNode.foreach(_.destroy())
        val (_, node) = Owner.withNew { f(value) }
        innerNode = Some(node)

      run(dep.now())
      val cancel = dep.subscribePost(run)
      ((), () => { cancel(); innerNode.foreach(_.destroy()) })
    }

  /** Subscribes to a [[Var]] in the **Pre** phase (before DOM updates).
    *
    * Unlike [[effect]], `layoutEffect` does **not** run on initial creation;
    * it fires only on subsequent changes to `dep`. Unsubscribes on release.
    */
  def layoutEffect[A](dep: Var[A])(f: A => Unit): ReactiveScope[Unit] =
    ReactiveScope { () =>
      val cancel = dep.subscribePre(f)
      ((), cancel)
    }

  /** Subscribes to a [[Signal]] in the **Pre** phase (before DOM updates).
    *
    * Behaves identically to the [[Var]] overload.
    */
  def layoutEffect[A](dep: Signal[A])(f: A => Unit): ReactiveScope[Unit] =
    ReactiveScope { () =>
      val cancel = dep.subscribePre(f)
      ((), cancel)
    }
