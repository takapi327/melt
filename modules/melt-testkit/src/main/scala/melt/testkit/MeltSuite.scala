/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testkit

import scala.collection.mutable
import scala.concurrent.{ Future, Promise }
import scala.scalajs.concurrent.JSExecutionContext
import scala.scalajs.js
import scala.scalajs.js.timers.*

import org.scalajs.dom

/** Base class for testing Melt components.
  *
  * Extend this class and call [[mount]] inside each test to attach a component
  * to the DOM. Every mounted component is automatically removed after the test
  * completes, keeping the DOM clean between tests.
  *
  * {{{
  * class CounterSpec extends MeltSuite:
  *   test("renders initial count") {
  *     val c = mount(Counter.create(Counter.Props("Test", 5)))
  *     assertEquals(c.text("h1"), "Test")
  *     assertEquals(c.text("p"), "Count: 5")
  *   }
  *
  *   test("increments on click") {
  *     val c = mount(Counter.create(Counter.Props("Test", 0)))
  *     c.click("button")
  *     assertEquals(c.text("p"), "Count: 1")
  *   }
  * }}}
  */
abstract class MeltSuite extends munit.FunSuite:

  /** Provides the Scala.js microtask queue as the implicit `ExecutionContext`.
    *
    * Made available as a `given` so that subclasses can use `Future` operations
    * (e.g. `.map`, `.flatMap`, `.failed`) without a separate import.
    */
  given scala.concurrent.ExecutionContext = JSExecutionContext.queue

  /** Containers created during the current test — cleaned up in [[afterEach]]. */
  private val _containers: mutable.ListBuffer[dom.html.Div] =
    mutable.ListBuffer.empty

  /** Removes all mounted containers after each test. */
  override def afterEach(context: AfterEach): Unit =
    _containers.foreach { c =>
      if c.parentNode != null then c.parentNode.removeChild(c)
    }
    _containers.clear()
    super.afterEach(context)

  /** Mounts `element` into a fresh `<div>` appended to `document.body` and
    * returns a [[MountedComponent]] handle for querying and interacting with it.
    *
    * The container is automatically removed after the test (see [[afterEach]]).
    *
    * @param element a DOM element produced by a generated component's `create()` method
    * @return a [[MountedComponent]] scoped to the new container
    */
  /** Repeatedly runs `assertion` until it succeeds or `timeout` milliseconds elapse.
    *
    * Useful for components whose state changes asynchronously (e.g. via `AsyncState`
    * or `setTimeout`). The returned `Future` is automatically awaited by munit.
    *
    * {{{
    * test("loads data") {
    *   val c = mount(UserProfile.create())
    *   waitFor { () =>
    *     assertEquals(c.text(".username"), "Alice")
    *   }
    * }
    * }}}
    *
    * @param assertion a function that runs assertions; must not throw when the expected
    *                  state has been reached
    * @param timeout   maximum wait time in milliseconds (default 1000)
    * @param interval  polling interval in milliseconds (default 50)
    */
  def waitFor(
    assertion: () => Unit,
    timeout:   Int = 1000,
    interval:  Int = 50
  )(using scala.concurrent.ExecutionContext): Future[Unit] =
    val promise   = Promise[Unit]()
    val startTime = js.Date.now()
    def attempt(): Unit =
      try
        assertion()
        promise.success(())
      catch
        case e: Throwable =>
          if js.Date.now() - startTime >= timeout.toDouble then
            promise.failure(
              new AssertionError(s"waitFor timed out after ${ timeout }ms. Last error: ${ e.getMessage }")
            )
          else setTimeout(interval.toDouble)(attempt())
    attempt()
    promise.future

  def mount(element: dom.Element): MountedComponent =
    val container = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    dom.document.body.appendChild(container)
    container.appendChild(element)
    _containers += container
    MountedComponent(container)
