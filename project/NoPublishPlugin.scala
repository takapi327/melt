import sbt._
import sbt.Keys._

/** publish を無効化するプラグイン。サンプルプロジェクト等に適用する。 */
object NoPublishPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def projectSettings: Seq[Setting[_]] = Seq(
    publish      / skip := true,
    publishLocal / skip := true
  )
}
