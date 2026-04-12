addSbtPlugin("org.scala-js"       % "sbt-scalajs"                   % "1.21.0")
addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.10")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject"      % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.scalameta"      % "sbt-scalafmt"                  % "2.5.6")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"                  % "0.14.6")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"                    % "5.10.0")
addSbtPlugin("com.github.sbt"     % "sbt-github-actions"            % "0.24.0")
addSbtPlugin("com.eed3si9n"       % "sbt-assembly"                  % "2.3.1")
addSbtPlugin("io.spray"           % "sbt-revolver"                  % "0.10.0")
// sbt-ci-release will be added when publishing to Maven Central.
// Excluded in Phase 0 because sbt-git (a dependency of sbt-ci-release) throws NoWorkTreeException in git worktree environments.
// addSbtPlugin("com.github.sbt" % "sbt-ci-release" % "1.5.12")
