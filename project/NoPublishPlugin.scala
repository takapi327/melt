import sbt.{ *, given }
import sbt.Keys.*

/** Plugin that disables publishing. Apply to example projects and similar. */
object NoPublishPlugin extends AutoPlugin {
  override def trigger = noTrigger
  override def projectSettings: Seq[Setting[?]] = Seq(
    publish / skip      := true,
    publishLocal / skip := true
  )
}
