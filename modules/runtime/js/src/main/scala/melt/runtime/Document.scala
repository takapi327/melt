/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

import org.scalajs.dom

/** Reactive bindings for `document` properties and events. */
object Document:

  /** Reactively sets the document title from a Signal. */
  def title(t: Signal[String]): Unit =
    dom.document.title = t.value
    val cancel = t.subscribe(s => dom.document.title = s)
    Cleanup.register(cancel)

  /** Sets the document title from a Var. */
  def title(t: Var[String]): Unit =
    title(t.signal)

  /** Sets the document title to a static string. */
  def title(t: String): Unit =
    dom.document.title = t

  /** Registers a document event listener with automatic cleanup. */
  def on(event: String)(handler: melt.runtime.dom.Event => Unit): Unit =
    val listener: scalajs.js.Function1[dom.Event, Unit] =
      (e: dom.Event) => handler(melt.runtime.dom.Conversions.wrap(e))
    dom.document.addEventListener(event, listener)
    Cleanup.register(() => dom.document.removeEventListener(event, listener))

  // ── Bind directives (<melt:document bind:prop={v}>) ────────────────────

  /** One-way binding: `document.visibilityState → Var[String]` (read-only).
    *
    * Mirrors Svelte 5's `bind_property('visibilityState', 'visibilitychange', document, set)`.
    */
  def bindVisibilityState(v: Var[String]): Unit =
    v.set(dom.document.visibilityState)
    val listener: scalajs.js.Function1[dom.Event, Unit] = _ => v.set(dom.document.visibilityState)
    dom.document.addEventListener("visibilitychange", listener)
    Cleanup.register(() => dom.document.removeEventListener("visibilitychange", listener))

  /** One-way binding: `document.fullscreenElement → Var[Option[dom.Element]]` (read-only).
    *
    * Mirrors Svelte 5's `bind_property('fullscreenElement', 'fullscreenchange', document, set)`.
    * Svelte returns `Element | null`; Melt uses `Option[dom.Element]` for type safety.
    */
  def bindFullscreenElement(v: Var[Option[dom.Element]]): Unit =
    v.set(Option(dom.document.fullscreenElement))
    val listener: scalajs.js.Function1[dom.Event, Unit] =
      _ => v.set(Option(dom.document.fullscreenElement))
    dom.document.addEventListener("fullscreenchange", listener)
    Cleanup.register(() => dom.document.removeEventListener("fullscreenchange", listener))

  /** One-way binding: `document.pointerLockElement → Var[Option[dom.Element]]` (read-only).
    *
    * Mirrors Svelte 5's `bind_property('pointerLockElement', 'pointerlockchange', document, set)`.
    */
  def bindPointerLockElement(v: Var[Option[dom.Element]]): Unit =
    v.set(Option(dom.document.pointerLockElement))
    val listener: scalajs.js.Function1[dom.Event, Unit] =
      _ => v.set(Option(dom.document.pointerLockElement))
    dom.document.addEventListener("pointerlockchange", listener)
    Cleanup.register(() => dom.document.removeEventListener("pointerlockchange", listener))

  /** One-way binding: `document.activeElement → Var[Option[dom.Element]]` (read-only).
    *
    * Mirrors Svelte 5's `bind_active_element(set)`:
    * - `focus` / `blur` はバブルしないため `focusin` / `focusout` を使用
    * - `focusout` 時に `relatedTarget` が存在する場合はフォーカスが別要素に移動したのみなので
    *   更新しない（Svelte 5 の JSDOM workaround と同一ロジック）
    * - それ以外（`focusin` またはドキュメント外へ）は `document.activeElement` を読み取る
    */
  def bindActiveElement(v: Var[Option[dom.Element]]): Unit =
    v.set(Option(dom.document.activeElement))
    val listener: scalajs.js.Function1[dom.Event, Unit] = e =>
      if e.`type` == "focusout" && e.asInstanceOf[dom.FocusEvent].relatedTarget != null then () // 別要素へのフォーカス移動 — 更新しない
      else v.set(Option(dom.document.activeElement))
    dom.document.addEventListener("focusin", listener)
    dom.document.addEventListener("focusout", listener)
    Cleanup.register(() => {
      dom.document.removeEventListener("focusin", listener)
      dom.document.removeEventListener("focusout", listener)
    })
