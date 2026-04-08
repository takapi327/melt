/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Manages insertion and removal of elements into `document.head`.
  *
  * Each call to [[appendChild]] inserts the given element into `document.head`
  * and registers a cleanup that removes it when the component is destroyed.
  * This mirrors the lifecycle of Svelte's `<svelte:head>`.
  *
  * Reactive children (e.g. `<title>{pageTitle}</title>`) work naturally because
  * the child element is constructed by the normal code-gen path — [[Bind.text]] /
  * [[Bind.attr]] subscriptions are already registered in the current [[Cleanup]]
  * scope before [[appendChild]] is called.
  */
object Head:

  /** Appends `child` to `document.head` and registers a cleanup to remove it. */
  def appendChild(child: dom.Element): Unit =
    dom.document.head.appendChild(child)
    Cleanup.register(() => {
      if dom.document.head.contains(child) then
        dom.document.head.removeChild(child)
        ()
    })
