import sbt.*

import sbtghactions.GenerativePlugin.autoImport.*
import sbtghactions.UseRef

import JavaVersions.*
import ScalaVersions.*

object Workflows:

  /** Condition used to gate artifact upload/download steps to tag-push releases only. */
  private val publishCond: String =
    "github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/v'))"

  /** Matrix projects whose tests run in `JSDOMNodeJSEnv`.
    *
    * That environment cannot start unless the `jsdom` npm package resolves from
    * the working directory — "You will need to `npm install jsdom` for the above
    * environment to work" (https://www.scala-js.org/doc/project/js-environments.html).
    * Keep this list in sync with the `jsEnv := ... JSDOMNodeJSEnv()` settings in
    * build.sbt; a project added there but not here fails at `loadedTestFrameworks`.
    */
  private val jsdomProjects: List[String] = List("runtimeJS", "testkit", "meltkitJS")

  private val jsdomCond: Option[String] =
    Some(s"contains('${ jsdomProjects.mkString(" ") }', matrix.project)")

  /** Installs the workspace's Node dependencies before sbt runs.
    *
    * pnpm is set up before `setup-node` on purpose: the latter's `cache: pnpm`
    * needs the pnpm binary to already be on PATH. No pnpm version is pinned here —
    * `pnpm/action-setup` reads it from the `packageManager` field of package.json,
    * so the version lives in exactly one place.
    */
  val nodeSetupSteps: Seq[WorkflowStep] = Seq(
    WorkflowStep.Use(
      UseRef.Public("pnpm", "action-setup", "v6"),
      name = Some("Setup pnpm"),
      cond = jsdomCond
    ),
    WorkflowStep.Use(
      UseRef.Public("actions", "setup-node", "v4"),
      name   = Some("Setup Node.js"),
      params = Map("node-version" -> "22", "cache" -> "pnpm"),
      cond   = jsdomCond
    ),
    WorkflowStep.Run(
      List("pnpm install --frozen-lockfile"),
      name = Some("Install Node dependencies (jsdom)"),
      cond = jsdomCond
    )
  )

  /** All target directories that must be archived for the publish job. */
  private val allTargetDirs: List[String] = List(
    "modules/compiler/jvm/target",
    "modules/compiler/js/target",
    "modules/compiler/native/target",
    "plugins/sbt-melt/target",
    "plugins/sbt-meltkit/target",
    "modules/runtime/target",
    "modules/testkit/target",
    "editors/language-server/target",
    "target",
    "project/target"
  )

  /** Projects included in the build matrix. */
  private val matrixProjects: List[String] = List("compilerJVM", "compilerJS", "compilerNative")

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

  /** Download steps for the publish job: one download + inflate per project variant. */
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
          List("sbt --server publishLocal"),
          name = Some("sbt publishLocal")
        ),
        WorkflowStep.Run(
          List("sbt --server scripted"),
          name = Some("sbt scripted")
        )
      ),
      scalas = List(scala3),
      javas  = List(JavaSpec.corretto(java17), JavaSpec.corretto(java21))
    )
  )
