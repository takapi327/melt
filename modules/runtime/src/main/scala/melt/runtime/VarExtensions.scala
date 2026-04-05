/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package melt.runtime

// ── Numeric extensions ──────────────────────────────────────────────────────

extension (v: Var[Int])
  def +=(n: Int): Unit = v.update(_ + n)
  def -=(n: Int): Unit = v.update(_ - n)
  def *=(n: Int): Unit = v.update(_ * n)

extension (v: Var[Long])
  def +=(n: Long): Unit = v.update(_ + n)
  def -=(n: Long): Unit = v.update(_ - n)

extension (v: Var[Double])
  def +=(n: Double): Unit = v.update(_ + n)
  def -=(n: Double): Unit = v.update(_ - n)

// ── String extension ─────────────────────────────────────────────────────────

extension (v: Var[String])
  def +=(s: String): Unit = v.update(_ + s)

// ── Boolean extension ────────────────────────────────────────────────────────

extension (v: Var[Boolean])
  def toggle(): Unit = v.update(!_)

// ── Collection extensions ────────────────────────────────────────────────────

extension [A](v: Var[List[A]])
  def append(item: A): Unit                             = v.update(_ :+ item)
  def prepend(item: A): Unit                            = v.update(item :: _)
  def removeWhere(pred: A => Boolean): Unit             = v.update(_.filterNot(pred))
  def removeAt(index: Int): Unit                        = v.update(_.patch(index, Nil, 1))
  def mapItems(f: A => A): Unit                         = v.update(_.map(f))
  def updateWhere(pred: A => Boolean)(f: A => A): Unit  = v.update(_.map(item => if pred(item) then f(item) else item))
  def clear(): Unit                                     = v.set(List.empty)
  def sortBy[B: Ordering](f: A => B): Unit              = v.update(_.sortBy(f))
