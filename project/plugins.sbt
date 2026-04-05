addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.21.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.10")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.5.6")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"                  % "0.14.6")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"                    % "5.10.0")
// sbt-ci-release は Maven Central へのリリース時に追加予定
// git worktree 環境では sbt-git（sbt-ci-release の依存）が NoWorkTreeException を投げるため Phase 0 では除外
// addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
