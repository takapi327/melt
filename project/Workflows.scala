import sbt._

import sbtghactions.GenerativePlugin.autoImport._

import JavaVersions._
import ScalaVersions._

object Workflows {

  val installNativeDeps: WorkflowStep.Run = WorkflowStep.Run(
    commands = List(
      "sudo apt-get update",
      "sudo apt-get install -y clang libstdc++-12-dev"
    ),
    name = Some("Install Scala Native dependencies"),
    cond = Some("matrix.project == 'meltcNative'")
  )

  val sbtScripted: Def.Initialize[WorkflowJob] = Def.setting(
    WorkflowJob(
      id    = "sbtScripted",
      name  = "sbt scripted",
      steps = githubWorkflowJobSetup.value.toList ::: List(
        WorkflowStep.Run(
          List("sbt +publishLocal"),
          name = Some("sbt publishLocal")
        ),
        WorkflowStep.Run(
          List("sbt scripted"),
          name = Some("sbt scripted")
        )
      ),
      scalas = List(scala3),
      javas  = List(JavaSpec.corretto(java17), JavaSpec.corretto(java21))
    )
  )
}
