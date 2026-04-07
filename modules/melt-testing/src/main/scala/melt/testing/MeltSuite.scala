/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.testing

import scala.collection.mutable

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
  def mount(element: dom.Element): MountedComponent =
    val container = dom.document.createElement("div").asInstanceOf[dom.html.Div]
    dom.document.body.appendChild(container)
    container.appendChild(element)
    _containers += container
    MountedComponent(container)
