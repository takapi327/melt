import sbt._
import sbt.Keys._

/** Plugin that disables publishing. Apply to example projects and similar. */
object NoPublishPlugin extends AutoPlugin {
  override def trigger                          = noTrigger
  override def projectSettings: Seq[Setting[_]] = Seq(
    publish / skip      := true,
    publishLocal / skip := true
  )
}
