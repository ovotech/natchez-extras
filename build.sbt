val common = Seq(
  scalaVersion := "2.12.6",
  organization := "com.ovoenergy.effect",
  organizationName := "Ovo Energy",
  organizationHomepage := Some(url("http://www.ovoenergy.com")),
  bintrayRepository := "maven",
  bintrayOrganization := Some("ovotech"),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-encoding",
    "utf8",
    "-feature",
    "-Xfatal-warnings",
    "-Ypartial-unification",
    "-language:higherKinds"
  ),
  libraryDependencies ++= Seq(
    compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
    "org.typelevel" %% "cats-effect" % "0.10.1",
    "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
  )
)

lazy val root = (project in file("."))
  .settings(common ++ Seq(name := "effect-utils", publish := nop, publishLocal := nop))
  .aggregate(logging, currentTime, delay, kamonMetrics)

lazy val currentTime = project
  .in(file("current-time"))
  .settings(common :+ (name := "current-time"))

lazy val logging = project
  .settings(common :+ (name := "logging"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
    )
  )

lazy val delay = project
  .settings(common :+ (name := "delay"))
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % "0.10.5"
    )
  )

lazy val kamonMetrics = project
  .in(file("kamon-metrics"))
  .settings(common :+ (name := "kamon-metrics"))
  .settings(
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % "0.6.7"
    )
  )
