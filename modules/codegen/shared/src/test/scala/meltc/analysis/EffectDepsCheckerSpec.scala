/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package meltc.analysis

import meltc.MeltCompiler

class EffectDepsCheckerSpec extends munit.FunSuite:

  private def warnings(script: String): List[String] =
    val src    = s"""<script lang="scala">\n$script\n</script>\n<div></div>"""
    val result = MeltCompiler.compile(src, "Test.melt", "Test", "")
    result.warnings.map(_.message)

  private def effectWarnings(script: String): List[String] =
    warnings(script).filter(w => w.contains("effect") || w.contains("layoutEffect"))

  // ── No warning cases ──────────────────────────────────────────────────────

  test("effect with single dep: no warning when dep is in body via arg") {
    val w = effectWarnings("""
      val count = Var(0)
      effect(count) { n => println(n) }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("effect with single dep: no warning when dep.value used and dep is listed") {
    val w = effectWarnings("""
      val count = Var(0)
      effect(count) { _ => println(count.value) }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("effect with two deps: no warning when both deps are listed") {
    val w = effectWarnings("""
      val count = Var(0)
      val name  = Var("")
      effect(count, name) { (a, b) => println(s"$a $b") }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("3-dep effect: no warning when all deps are listed") {
    val w = effectWarnings("""
      val count  = Var(0)
      val name   = Var("")
      val userId = Var("")
      effect(count, name, userId) { (a, b, c) =>
        println(s"$a $b $c")
      }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("no reactive vars: no effect warnings") {
    val w = effectWarnings("""
      val x = 42
      effect(Var(x)) { _ => println("ok") }
    """)
    assert(w.isEmpty, w.toString)
  }

  // ── Warning cases ─────────────────────────────────────────────────────────

  test("effect with single dep: warns when another Var is used in body but not in deps") {
    val w = effectWarnings("""
      val count  = Var(0)
      val userId = Var("")
      effect(count) { n =>
        fetchData(n, userId.value)
      }
    """)
    // 'userId' should be reported as missing
    assert(w.exists(_.startsWith("'userId'")), w.toString)
    // 'count' is in deps — no separate warning for count
    assert(!w.exists(_.startsWith("'count'")), w.toString)
  }

  test("3-dep effect: warns when a Var used in body is not in deps list") {
    val w = effectWarnings("""
      val count  = Var(0)
      val name   = Var("")
      val userId = Var("")
      val extra  = Var(false)
      effect(count, name, userId) { (a, b, c) =>
        println(s"$a $b $c ${extra.value}")
      }
    """)
    assert(w.exists(_.startsWith("'extra'")), w.toString)
    assert(!w.exists(_.startsWith("'count'")), w.toString)
    assert(!w.exists(_.startsWith("'name'")), w.toString)
    assert(!w.exists(_.startsWith("'userId'")), w.toString)
  }

  test("layoutEffect: warns when a Var used in body is not in deps list") {
    val w = effectWarnings("""
      val messages = Var(List.empty[String])
      val scroll   = Var(0)
      layoutEffect(messages) { _ =>
        println(scroll.value)
      }
    """)
    assert(w.exists(_.startsWith("'scroll'")), w.toString)
    assert(!w.exists(_.startsWith("'messages'")), w.toString)
  }

  test("warns with hint containing the missing dep name") {
    val w = effectWarnings("""
      val count  = Var(0)
      val userId = Var("")
      effect(count) { _ => println(userId.value) }
    """)
    val msg = w.find(_.startsWith("'userId'")).getOrElse(fail(s"no warning found: $w"))
    assert(msg.contains("hint"), msg)
    assert(msg.contains("userId"), msg)
  }

  // ── False-positive suppression ────────────────────────────────────────────

  test("no warning when Var name appears only in string literal") {
    val w = effectWarnings("""
      val count = Var(0)
      effect(count) { _ => println("count is not reactive here") }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("no warning when Var name appears only in line comment") {
    val w = effectWarnings("""
      val count = Var(0)
      val name  = Var("")
      effect(count) { n =>
        // name is intentionally excluded
        println(n)
      }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("no warning for local val that shadows reactive var name") {
    val w = effectWarnings("""
      val count = Var(0)
      val name  = Var("")
      effect(count) { _ =>
        val name = "local"
        println(name)
      }
    """)
    assert(w.isEmpty, w.toString)
  }

  // ── melt-ignore suppression ───────────────────────────────────────────────

  test("melt-ignore: exhaustive-deps suppresses warning on that line") {
    val w = effectWarnings("""
      val count  = Var(0)
      val userId = Var("")
      effect(count) { _ => println(userId.value) } // melt-ignore: exhaustive-deps
    """)
    assert(w.isEmpty, w.toString)
  }

  // ── Direct access warnings ────────────────────────────────────────────────

  test("direct access: warns when dep accessed as bare var inside body") {
    val w = effectWarnings("""
      val user   = Var(Option.empty[String])
      val loaded = Var(false)
      effect(user, loaded) { (v1, v2) =>
        println(loaded)
        println(user)
      }
    """)
    assert(w.exists(_.startsWith("'user'")), w.toString)
    assert(w.exists(_.startsWith("'loaded'")), w.toString)
  }

  test("direct access: warns when dep accessed via .value instead of arg") {
    val w = effectWarnings("""
      val count = Var(0)
      val name  = Var("")
      effect(count, name) { (a, b) =>
        println(count.value)
        println(b)
      }
    """)
    assert(w.exists(_.startsWith("'count'")), w.toString)
    assert(!w.exists(_.startsWith("'name'")), w.toString)
  }

  test("direct access: no warning when _ param (trigger pattern)") {
    val w = effectWarnings("""
      val count = Var(0)
      effect(count) { _ => println(count.value) }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("direct access: no warning when args used correctly") {
    val w = effectWarnings("""
      val user   = Var(Option.empty[String])
      val loaded = Var(false)
      effect(user, loaded) { (v1, v2) =>
        println(v1)
        println(v2)
      }
    """)
    assert(w.isEmpty, w.toString)
  }

  // ── Unused dep warnings ───────────────────────────────────────────────────

  test("unused dep: warns when dep listed but not referenced (dep.value used for other dep)") {
    val w = effectWarnings("""
      val user   = Var(Option.empty[String])
      val loaded = Var(false)
      effect(user, loaded) { (v1, v2) =>
        println(user.value)
      }
    """)
    // user accessed directly → direct access warning
    assert(w.exists(_.startsWith("'user'")), w.toString)
    // loaded not referenced at all → unused warning
    assert(w.exists(_.startsWith("'loaded'")), w.toString)
  }

  test("unused dep: warns when dep listed but only other arg is used") {
    val w = effectWarnings("""
      val user   = Var(Option.empty[String])
      val loaded = Var(false)
      effect(user, loaded) { (v1, v2) =>
        println(v1)
      }
    """)
    assert(w.exists(_.startsWith("'loaded'")), w.toString)
    assert(!w.exists(_.startsWith("'user'")), w.toString)
  }

  test("unused dep: no warning when all deps accessed via positional args") {
    val w = effectWarnings("""
      val count = Var(0)
      val name  = Var("")
      effect(count, name) { (a, b) => println(s"$a $b") }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("unused dep: no warning for single dep accessed via arg") {
    val w = effectWarnings("""
      val count = Var(0)
      effect(count) { n => println(n) }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("unused dep: no warning when _ param used (trigger pattern)") {
    val w = effectWarnings("""
      val trigger = Var(0)
      val data    = Var("")
      effect(trigger, data) { (_, d) => println(d) }
    """)
    assert(w.isEmpty, w.toString)
  }

  test("unused dep: melt-ignore suppresses unused dep warning") {
    val w = effectWarnings("""
      val user   = Var(Option.empty[String])
      val loaded = Var(false)
      effect(user, loaded) { (v1, v2) => println(v1) } // melt-ignore: exhaustive-deps
    """)
    assert(w.isEmpty, w.toString)
  }

  test("unused dep: warns for layoutEffect when dep not referenced via arg or .value") {
    val w = effectWarnings("""
      val messages = Var(List.empty[String])
      val scroll   = Var(0)
      layoutEffect(messages, scroll) { (a, b) =>
        println(a)
      }
    """)
    assert(w.exists(_.startsWith("'scroll'")), w.toString)
    assert(!w.exists(_.startsWith("'messages'")), w.toString)
  }

  // ── Signal derived vars ───────────────────────────────────────────────────

  test("warns when a derived Signal (.map) is used in body but not in deps") {
    val w = effectWarnings("""
      val count   = Var(0)
      val doubled = count.map(_ * 2)
      val name    = Var("")
      effect(name) { _ => println(doubled.value) }
    """)
    assert(w.exists(_.startsWith("'doubled'")), w.toString)
  }

  // ── No reactive vars case ─────────────────────────────────────────────────

  test("no warnings when script has no Var declarations") {
    val w = effectWarnings("""
      val x = 42
      println(x)
    """)
    assert(w.isEmpty, w.toString)
  }
