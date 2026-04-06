import sbt._

import sbtghactions.GenerativePlugin.autoImport._
import sbtghactions.UseRef

import JavaVersions._
import ScalaVersions._

object Workflows {

  /** Condition used to gate artifact upload/download steps to tag-push releases only. */
  private val publishCond: String =
    "github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/v'))"

  /** All target directories that must be archived for the publish job. */
  private val allTargetDirs: List[String] = List(
    "modules/meltc/jvm/target",
    "modules/meltc/js/target",
    "modules/meltc/native/target",
    "modules/sbt-meltc/target",
    "modules/runtime/target",
    "modules/melt-testing/target",
    "editors/language-server/target",
    "target",
    "project/target"
  )

  /** Projects included in the build matrix. */
  private val matrixProjects: List[String] = List("meltcJVM", "meltcJS", "meltcNative")

  val installNativeDeps: WorkflowStep.Run = WorkflowStep.Run(
    commands = List(
      "sudo apt-get update",
      "sudo apt-get install -y clang libstdc++-12-dev"
    ),
    name = Some("Install Scala Native dependencies"),
    cond = Some("matrix.project == 'meltcNative'")
  )

  /** Upload steps matching ldbc's pattern:
    *   1. mkdir -p  (ensure all target dirs exist)
    *   2. tar       (compress)
    *   3. upload    (artifact name includes matrix.project to avoid conflicts)
    * All three steps are gated behind the publishCond so they only run on tag pushes.
    */
  val uploadSteps: Seq[WorkflowStep] = Seq(
    WorkflowStep.Run(
      List("mkdir -p " + allTargetDirs.mkString(" ")),
      name = Some("Make target directories"),
      cond = Some(publishCond)
    ),
    WorkflowStep.Run(
      List("tar cf targets.tar " + allTargetDirs.mkString(" ")),
      name = Some("Compress target directories"),
      cond = Some(publishCond)
    ),
    WorkflowStep.Use(
      UseRef.Public("actions", "upload-artifact", "v4"),
      name   = Some("Upload target directories"),
      cond   = Some(publishCond),
      params = Map(
        "name" -> "target-${{ matrix.os }}-${{ matrix.java }}-${{ matrix.scala }}-${{ matrix.project }}",
        "path" -> "targets.tar"
      )
    )
  )

  /** Download steps for the publish job: one download + inflate per project variant.
    * Only Scala 3.3.7 (the LTS / default) is downloaded since publish uses the first Scala version.
    */
  val downloadSteps: Seq[WorkflowStep] =
    matrixProjects.flatMap { proj =>
      Seq(
        WorkflowStep.Use(
          UseRef.Public("actions", "download-artifact", "v4"),
          name   = Some(s"Download target directories ($scala3, $proj)"),
          params = Map("name" -> s"target-$${{ matrix.os }}-$${{ matrix.java }}-$scala3-$proj")
        ),
        WorkflowStep.Run(
          List("tar xf targets.tar", "rm targets.tar"),
          name = Some(s"Inflate target directories ($scala3, $proj)")
        )
      )
    }

  val sbtScripted: Def.Initialize[WorkflowJob] = Def.setting(
    WorkflowJob(
      id    = "sbtScripted",
      name  = "sbt scripted",
      steps = githubWorkflowJobSetup.value.toList ::: List(
        WorkflowStep.Run(
          List("sbt publishLocal"),
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
