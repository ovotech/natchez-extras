val common = Seq(
  ThisBuild / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.ScalaLibrary,
  scalaVersion := "2.13.1",
  organization := "com.ovoenergy.effect",
  organizationName := "OVO Energy",
  organizationHomepage := Some(url("http://www.ovoenergy.com")),
  bintrayRepository := "maven",
  bintrayOrganization := Some("ovotech"),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  libraryDependencies ++= Seq(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    compilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full),
    "org.typelevel" %% "cats-core" % "2.1.0",
    "org.typelevel" %% "cats-effect" % "2.1.0",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.2" % "test"
  )
)

lazy val metricsCommon = project
  .in(file("metrics-common")).settings(common :+ (name := "metrics-common"))

val natchezVersion = "0.0.11"
val http4sVersion = "0.21.0-RC4"
val circeVersion = "0.12.2"
val fs2Version = "2.2.2"

lazy val natchezDatadog = project
  .in(file("natchez-datadog"))
  .settings(common :+ (name := "natchez-datadog"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core"         % natchezVersion,
      "org.http4s"   %% "http4s-dsl"           % http4sVersion,
      "org.http4s"   %% "http4s-circe"         % http4sVersion,
      "org.http4s"   %% "http4s-client"        % http4sVersion,
      "io.circe"     %% "circe-core"           % circeVersion,
      "io.circe"     %% "circe-generic"        % circeVersion,
      "io.circe"     %% "circe-generic-extras" % circeVersion,
      "io.circe"     %% "circe-parser"         % circeVersion
    )
  )

lazy val natchezSlf4j = project
  .in(file("natchez-slf4j"))
  .settings(common :+ (name := "natchez-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.slf4j" % "slf4j-api" % "1.7.28",
      "uk.org.lidalia" % "slf4j-test" % "1.2.0" % Test
    )
  )

val silencerVersion = "1.4.4"
lazy val natchezDoobie = project
  .in(file("natchez-doobie"))
  .settings(common :+ (name := "natchez-doobie"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.tpolecat" %% "doobie-core"  % "0.8.4",
      "org.tpolecat" %% "doobie-h2"    % "0.8.4",
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full,
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    ),
  )

lazy val natchezCombine = project
  .in(file("natchez-combine"))
  .settings(common :+ (name := "natchez-combine"))
  .settings(libraryDependencies += "org.tpolecat" %% "natchez-core" % natchezVersion)

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

lazy val root = (project in file("."))
  .settings(
    common ++ Seq(
      name := "effect-utils",
      publish := nop,
      publishLocal := nop
    ))
  .aggregate(
    metricsCommon,
    datadogMetrics,
    natchezDatadog,
    natchezCombine,
    natchezSlf4j,
    natchezDoobie
  )

Global / onChangedBuildSource := ReloadOnSourceChanges
