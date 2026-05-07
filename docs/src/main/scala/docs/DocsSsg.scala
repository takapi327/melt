/**
 * Copyright (c) 2026 by Takahiko Tominaga
 * This software is licensed under the Apache License, Version 2.0 (the "License").
 * For more information see LICENSE or https://www.apache.org/licenses/LICENSE-2.0
 */

package docs

import meltkit.{ MeltKit, SyncRunner, Template, ViteManifest }
import meltkit.ssg.SsgApp

type Id = [A] =>> A

object DocsSsg extends SsgApp[Id]:

  private val langs    = List("en", "ja")
  private val guides   = List(
    "introduction", "installation", "quick-start",
    "components", "template-syntax", "reactivity", "computed",
    "effects", "events", "lifecycle", "control-flow",
    "special-elements", "transitions", "trusted-html", "css",
    "testing", "routing", "ssr", "ssg", "adapters"
  )
  private val apis     = List(
    "template-syntax", "runtime", "meltkit", "meltkit-ssg", "compiler", "sbt-plugin"
  )
  private val examples = List("counter", "todo-app")

  override val kit: MeltKit[Id] = new MeltKit[Id]

  override val paths: List[String] =
    langs.flatMap { lang =>
      List(s"/$lang") ++
        guides.map(p => s"/$lang/guide/$p") ++
        apis.map(p => s"/$lang/api/$p") ++
        List(s"/$lang/examples") ++
        examples.map(p => s"/$lang/examples/$p") ++
        List(s"/$lang/changelog")
    } :+ "/"

  override val template: Template =
    Template.fromString(
      """|<!doctype html>
         |<html lang="%melt.lang%">
         |<head>
         |  <meta charset="UTF-8">
         |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
         |  <title>%melt.title%</title>
         |  %melt.head%
         |</head>
         |<body>%melt.body%</body>
         |</html>""".stripMargin
    )

  override val manifest: ViteManifest = ViteManifest.empty

  override given syncRunner: SyncRunner[Id] with
    override def runSync[A](fa: A): A = fa
