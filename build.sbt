val common = Seq(
  scalaVersion := "2.12.9",
  organization := "com.ovoenergy.effect",
  organizationName := "OVO Energy",
  organizationHomepage := Some(url("http://www.ovoenergy.com")),
  bintrayRepository := "maven",
  bintrayOrganization := Some("ovotech"),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  libraryDependencies ++= Seq(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"),
    "org.typelevel" %% "cats-core" % "2.0.0",
    "org.typelevel" %% "cats-effect" % "2.0.0",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.2" % "test"
  )
)

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

val natchezVersion = "0.0.10"
val http4sVersion = "0.21.0-M5"
val circeVersion = "0.12.1"
val fs2Version = "2.0.1"

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
      "org.tpolecat" %% "natchez-core" % "0.0.8",
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
  .settings(common ++ Seq(name := "effect-utils", publish := nop, publishLocal := nop))
  .aggregate(logging, metricsCommon, kamonMetrics, datadogMetrics, natchezDatadog, natchezCombine, natchezSlf4j)
