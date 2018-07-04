import sbtrelease.ReleaseStateTransformations._


resolvers += Resolver.sonatypeRepo("releases")

val bintrayReleaseProcess: Seq[ReleaseStep] = Seq(
  releaseStepTask(test),
  inquireVersions,
  setReleaseVersion,
  releaseStepTask(publish),
  commitReleaseVersion,
  setNextVersion,
  commitNextVersion
)

val common = Seq(
  scalaVersion := "2.12.6",
  organization := "com.ovoenergy",
  organizationName := "Ovo Energy",
  organizationHomepage := Some(url("http://www.ovoenergy.com")),
  bintrayRepository := "maven",
  bintrayOrganization := Some("ovotech"),
  releaseCommitMessage := s"Setting version of ${name.value} to ${version.value} [ci skip]",
  releaseProcess := bintrayReleaseProcess,
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-encoding",
    "utf8",
    "-feature",
    "-Xfatal-warnings",
    "-language:higherKinds"
  ),
  libraryDependencies ++= Seq(
    compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
    "org.typelevel" %% "cats-effect" % sys.env.getOrElse("CATS_EFFECT_VERSION", "0.10.1"),
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
)

lazy val root = (project in file("."))
  .settings(common :+ (name := "effect-util"))

val logging = project
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
    )
  )

