import microsites.MicrositesPlugin.autoImport.micrositeDescription

val scala213Version = "2.13.10"
val scala3Version = "3.3.0"

val scalaVersions = Seq(scala213Version, scala3Version)

ThisBuild / organization := "com.ovoenergy"

ThisBuild / organizationName := "OVO Energy"

ThisBuild / organizationHomepage := Some(url("http://www.ovoenergy.com"))

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/ovotech/natchez-extras"),
    "scm:git@github.com:ovotech/natchez-extras.git"
  )
)

ThisBuild / homepage := Some(url("https://ovotech.github.io/natchez-extras/"))

ThisBuild / licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))

ThisBuild / publishMavenStyle := true

ThisBuild / publishTo := sonatypePublishToBundle.value

ThisBuild / scalafmtOnCompile := true

ThisBuild / developers ++= List(
  Developer("tomverran", "Tom Verran", "github@tomverran.co.uk", url("https://github.com/tomverran"))
)

ThisBuild / credentials += (
  for {
    user <- sys.env.get("SONATYPE_USERNAME")
    pass <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", user, pass)
).getOrElse(Credentials(Path.userHome / ".sbt" / ".sonatype_credentials"))

val common = Seq(
  Test / fork := true,
  git.useGitDescribe := true,
  libraryDependencies ++= Seq(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    compilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
  ).filterNot(_ => scalaVersion.value.startsWith("3.")),
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % "2.9.0",
    "org.typelevel" %% "cats-effect" % "3.5.1",
    "org.scalameta" %% "munit" % "0.7.29" % Test,
    "org.scalacheck" %% "scalacheck" % "1.17.0" % Test,
    "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test,
    "org.typelevel" %% "scalacheck-effect-munit" % "1.0.4" % Test
  )
)

lazy val metricsCommon = projectMatrix
  .in(file("natchez-extras-metrics"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-metrics"))

val log4catsVersion = "2.6.0"
val natchezVersion = "0.3.3"
val http4sMilestoneVersion = "1.0.0-M40"
val http4sStableVersion = "0.23.23"
val circeVersion = "0.14.3"
val slf4jVersion = "1.7.36"
val fs2Version = "3.8.0"
val doobieVersion = "1.0.0-RC2"

lazy val natchezDatadog = projectMatrix
  .in(file("natchez-extras-datadog"))
  .customRow(
    scalaVersions = scalaVersions,
    axisValues = Seq(Http4sVersion.Milestone, VirtualAxis.jvm),
    settings = List(
      name := "natchez-extras-datadog",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-dsl" % http4sMilestoneVersion,
        "org.http4s" %% "http4s-circe" % http4sMilestoneVersion,
        "org.http4s" %% "http4s-client" % http4sMilestoneVersion
      )
    )
  )
  .customRow(
    scalaVersions = scalaVersions,
    axisValues = Seq(Http4sVersion.Stable, VirtualAxis.jvm),
    settings = List(
      name := "natchez-extras-datadog-stable",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-dsl" % http4sStableVersion,
        "org.http4s" %% "http4s-circe" % http4sStableVersion,
        "org.http4s" %% "http4s-client" % http4sStableVersion
      )
    )
  )
  .enablePlugins(GitVersioning)
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion
    )
  )

lazy val natchezSlf4j = projectMatrix
  .in(file("natchez-extras-slf4j"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "uk.org.lidalia" % "slf4j-test" % "1.2.0" % Test
    )
  )

lazy val natchezHttp4s = projectMatrix
  .in(file("natchez-extras-http4s"))
  .customRow(
    scalaVersions = scalaVersions,
    axisValues = Seq(Http4sVersion.Milestone, VirtualAxis.jvm),
    settings = List(
      name := "natchez-extras-http4s",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-dsl" % http4sMilestoneVersion,
        "org.http4s" %% "http4s-client" % http4sMilestoneVersion
      )
    )
  )
  .customRow(
    scalaVersions = scalaVersions,
    axisValues = Seq(Http4sVersion.Stable, VirtualAxis.jvm),
    settings = List(
      name := "natchez-extras-http4s-stable",
      libraryDependencies ++= Seq(
        "org.http4s" %% "http4s-dsl" % http4sStableVersion,
        "org.http4s" %% "http4s-client" % http4sStableVersion
      )
    )
  )
  .dependsOn(natchezTestkit)
  .enablePlugins(GitVersioning)
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion
    )
  )

lazy val natchezLog4Cats = projectMatrix
  .in(file("natchez-extras-log4cats"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-log4cats"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.typelevel" %% "log4cats-core" % log4catsVersion
    )
  )

lazy val natchezTestkit = projectMatrix
  .in(file("natchez-extras-testkit"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-testkit"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion
    )
  )

lazy val natchezFs2 = projectMatrix
  .in(file("natchez-extras-fs2"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .dependsOn(natchezTestkit)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-fs2"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "kittens" % "3.0.0",
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )

lazy val natchezDoobie = projectMatrix
  .in(file("natchez-extras-doobie"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-doobie"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-h2" % doobieVersion % Test
    )
  )
  .dependsOn(core)

lazy val core = projectMatrix
  .in(file("natchez-extras-core"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(
    common ++ Seq(
      name := "natchez-extras-core",
    )
  )

lazy val natchezCombine = projectMatrix
  .in(file("natchez-extras-combine"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-combine"))
  .settings(libraryDependencies += "org.tpolecat" %% "natchez-core" % natchezVersion)

lazy val datadogMetrics = projectMatrix
  .in(file("natchez-extras-dogstatsd"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-dogstatsd"))
  .dependsOn(metricsCommon)
  .settings(
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,
    )
  )

lazy val ce3Utils = projectMatrix
  .in(file("natchez-ce3"))
  .jvmPlatform(scalaVersions = scalaVersions)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-ce3"))
  .settings(libraryDependencies += "org.tpolecat" %% "natchez-core" % natchezVersion)

val logbackVersion = "1.2.3"

lazy val datadogStable213 = natchezDatadog.finder(Http4sVersion.Stable, VirtualAxis.jvm)(scala213Version)
lazy val datadogMilestone213 =
  natchezDatadog.finder(Http4sVersion.Milestone, VirtualAxis.jvm)(scala213Version)

lazy val natchezHttp4sStable213 = natchezHttp4s.finder(Http4sVersion.Stable, VirtualAxis.jvm)(scala213Version)
lazy val natchezHttp4sMilestone213 =
  natchezHttp4s.finder(Http4sVersion.Milestone, VirtualAxis.jvm)(scala213Version)

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(MicrositesPlugin)
  .dependsOn(
    ce3Utils.jvm(scala213Version),
    datadogMetrics.jvm(scala213Version),
    natchezDoobie.jvm(scala213Version),
    datadogStable213,
    natchezCombine.jvm(scala213Version),
    natchezSlf4j.jvm(scala213Version),
    natchezFs2.jvm(scala213Version),
    natchezHttp4sStable213,
    natchezLog4Cats.jvm(scala213Version)
  )
  .settings(
    scalaVersion := scala213Version,
    micrositeName := "natchez-extras",
    micrositeBaseUrl := "/natchez-extras",
    micrositeDocumentationUrl := "/natchez-extras/docs",
    micrositeDescription := "Datadog integrations for functional Scala",
    micrositeImgDirectory := (Compile / resourceDirectory).value / "microsite" / "img",
    micrositePalette := micrositePalette.value ++ Map("brand-primary" -> "#632CA6"),
    mdocVariables := Map(
      "VERSION" -> version.value.takeWhile(_ != '-'),
      "LOG4CATSVERSION" -> log4catsVersion,
      "HTTP4SVERSION" -> http4sStableVersion
    ),
    micrositePushSiteWith := GHPagesPlugin,
    micrositeGitterChannel := false,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % "0.23.12",
      "org.http4s" %% "http4s-blaze-server" % "0.23.12",
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion
    )
  )

lazy val root = (project in file("."))
  .settings(
    common ++ Seq(
      name := "natchez-extras",
      publish / skip := true
    )
  )
  .aggregate(core.projectRefs: _*)
  .aggregate(ce3Utils.projectRefs: _*)
  .aggregate(metricsCommon.projectRefs: _*)
  .aggregate(datadogMetrics.projectRefs: _*)
  .aggregate(natchezDatadog.projectRefs: _*)
  .aggregate(natchezCombine.projectRefs: _*)
  .aggregate(natchezSlf4j.projectRefs: _*)
  .aggregate(natchezDoobie.projectRefs: _*)
  .aggregate(natchezLog4Cats.projectRefs: _*)
  .aggregate(natchezHttp4s.projectRefs: _*)
  .aggregate(natchezFs2.projectRefs: _*)
  .aggregate(natchezTestkit.projectRefs: _*)

Global / onChangedBuildSource := ReloadOnSourceChanges
