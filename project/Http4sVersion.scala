import sbt.VirtualAxis

sealed abstract class Http4sVersion(
  override val idSuffix: String,
  override val directorySuffix: String
) extends VirtualAxis.WeakAxis

object Http4sVersion {
  object Milestone extends Http4sVersion(idSuffix = "", directorySuffix = "")
  object Stable extends Http4sVersion(idSuffix = "Stable", directorySuffix = "-stable")
}
