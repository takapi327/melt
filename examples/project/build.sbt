// scalajs-env-jsdom-nodejs は Scala 3 版が未公開のため _2.13 版を使用。
// sbt-scalajs 1.22.0 が scalajs-js-envs_3 / scalajs-env-nodejs_3 を提供済みのため
// _2.13 推移的依存 (org.scala-js 系 + scala-library) は除外する。
libraryDependencies += ("org.scala-js" % "scalajs-env-jsdom-nodejs_2.13" % "1.1.0")
  .excludeAll(
    ExclusionRule("org.scala-js"),
    ExclusionRule("org.scala-lang", "scala-library")
  )
