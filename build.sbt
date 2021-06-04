import microsites.MicrositesPlugin.autoImport.micrositeDescription

ThisBuild / scalaVersion := "2.13.5"

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
    compilerPlugin("org.typelevel" % "kind-projector" % "0.13.0" cross CrossVersion.full),
    "org.typelevel" %% "cats-core" % "2.6.1",
    "org.typelevel" %% "cats-effect" % "3.1.1",
    "org.scalatest" %% "scalatest" % "3.2.9" % Test,
    "org.scalacheck" %% "scalacheck" % "1.15.4" % Test,
    "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test
  ),
)

lazy val metricsCommon = project
  .in(file("natchez-extras-metrics"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-metrics"))

val log4catsVersion = "2.1.1"
val natchezVersion = "0.1.5"
val http4sVersion = "1.0.0-M23"
val circeVersion = "0.14.1"
val slf4jVersion = "1.7.30"
val fs2Version = "3.0.4"
val doobieVersion = "1.0.0-M5"

lazy val natchezDatadog = project
  .in(file("natchez-extras-datadog"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-datadog"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core"         % natchezVersion,
      "org.http4s"   %% "http4s-dsl"           % http4sVersion,
      "org.http4s"   %% "http4s-circe"         % http4sVersion,
      "org.http4s"   %% "http4s-client"        % http4sVersion,
      "io.circe"     %% "circe-core"           % circeVersion,
      "io.circe"     %% "circe-generic"        % circeVersion,
      "io.circe"     %% "circe-generic-extras" % circeVersion,
      "io.circe"     %% "circe-parser"         % circeVersion,
      "org.slf4j"    % "slf4j-api"             % slf4jVersion
)
  )

lazy val natchezSlf4j = project
  .in(file("natchez-extras-slf4j"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "uk.org.lidalia" % "slf4j-test" % "1.2.0" % Test
    )
  )

lazy val natchezHttp4s = project
  .in(file("natchez-extras-http4s"))
  .dependsOn(natchezTestkit)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-http4s"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core"  % natchezVersion,
      "org.http4s"   %% "http4s-client" % http4sVersion,
      "org.http4s"   %% "http4s-dsl"    % http4sVersion
    )
  )

lazy val natchezLog4Cats = project
  .in(file("natchez-extras-log4cats"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-log4cats"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.typelevel" %% "log4cats-core" % log4catsVersion
    )
  )

lazy val natchezTestkit = project
  .in(file("natchez-extras-testkit"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-testkit"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion
    )
  )

lazy val natchezFs2 = project
  .in(file("natchez-extras-fs2"))
  .dependsOn(natchezTestkit)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-fs2"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "kittens" % "2.3.2",
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )

lazy val natchezDoobie = project
  .in(file("natchez-extras-doobie"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-doobie"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.tpolecat" %% "doobie-core"  % doobieVersion,
      "org.tpolecat" %% "doobie-h2"    % doobieVersion % Test
    ),
  )

lazy val natchezCombine = project
  .in(file("natchez-extras-combine"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-combine"))
  .settings(libraryDependencies += "org.tpolecat" %% "natchez-core" % natchezVersion)

lazy val datadogMetrics = project
  .in(file("natchez-extras-dogstatsd"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-extras-dogstatsd"))
  .dependsOn(metricsCommon)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "claimant" % "0.2.0" % Test,
      "co.fs2" %% "fs2-core" % fs2Version,
      "co.fs2" %% "fs2-io" % fs2Version,
    )
  )

val logbackVersion = "1.2.3"

lazy val docs = project
  .in(file("docs"))
  .enablePlugins(MicrositesPlugin)
  .dependsOn(
    datadogMetrics,
    natchezDoobie,
    natchezDatadog,
    natchezCombine,
    natchezSlf4j,
    natchezFs2,
    natchezHttp4s,
    natchezLog4Cats
  )
  .settings(
    micrositeName := "natchez-extras",
    micrositeBaseUrl := "/natchez-extras",
    micrositeDocumentationUrl := "/natchez-extras/docs",
    micrositeDescription := "Datadog integrations for functional Scala",
    micrositeImgDirectory := (Compile / resourceDirectory).value / "microsite" / "img",
    micrositePalette := micrositePalette.value ++ Map("brand-primary" -> "#632CA6"),
    mdocVariables := Map(
      "VERSION" -> version.value.takeWhile(_ != '-'),
      "LOG4CATSVERSION" -> log4catsVersion,
      "HTTP4SVERSION" -> http4sVersion
    ),
    micrositePushSiteWith := GHPagesPlugin,
    micrositeGitterChannel := false,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "org.typelevel" %% "log4cats-slf4j" % log4catsVersion
    )
  )

lazy val root = (project in file("."))
  .settings(
    common ++ Seq(
      name := "natchez-extras",
      publish / skip := true
    ))
  .aggregate(
    metricsCommon,
    datadogMetrics,
    natchezDatadog,
    natchezCombine,
    natchezSlf4j,
    natchezDoobie,
    natchezLog4Cats,
    natchezHttp4s,
    natchezFs2,
    natchezTestkit
  )

Global / onChangedBuildSource := ReloadOnSourceChanges
