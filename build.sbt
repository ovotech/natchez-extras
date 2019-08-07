val common = Seq(
  scalaVersion := "2.12.7",
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
    compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"),
    "org.typelevel" %% "cats-core" % "1.4.0",
    "org.typelevel" %% "cats-effect" % "1.3.1",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test"
  )
)

lazy val root = (project in file("."))
  .settings(common ++ Seq(name := "effect-utils", publish := nop, publishLocal := nop))
  .aggregate(logging, metricsCommon, kamonMetrics, datadogMetrics)

lazy val logging = project
  .settings(common :+ (name := "logging"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
    )
  )

lazy val metricsCommon = project
  .in(file("metrics-common")).settings(common :+ (name := "metrics-common"))

lazy val kamonMetrics = project
  .in(file("metrics-kamon"))
  .settings(common :+ (name := "kamon-metrics"))
  .dependsOn(metricsCommon)
  .settings(
    libraryDependencies ++= Seq(
      "io.kamon" %% "kamon-core" % "1.1.0"
    )
  )

val fs2Version = "1.0.5"
lazy val datadogMetrics = project
  .in(file("metrics-datadog"))
  .settings(common :+ (name := "datadog-metrics"))
  .dependsOn(metricsCommon)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,
    )
  )
