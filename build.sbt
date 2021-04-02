import microsites.MicrositesPlugin.autoImport.micrositeDescription

scalaVersion in ThisBuild := "2.13.4"

classLoaderLayeringStrategy in ThisBuild := ClassLoaderLayeringStrategy.ScalaLibrary

organization in ThisBuild := "com.ovoenergy.effect"

organizationName in ThisBuild := "OVO Energy"

organizationHomepage in ThisBuild := Some(url("http://www.ovoenergy.com"))

val common = Seq(
  fork in Test := true,
  bintrayRepository := "maven",
  bintrayOrganization := Some("ovotech"),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  git.useGitDescribe := true,
  libraryDependencies ++= Seq(
    compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
    compilerPlugin("org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full),
    "org.typelevel" %% "cats-core" % "2.3.1",
    "org.typelevel" %% "cats-effect" % "2.3.1",
    "org.scalatest" %% "scalatest" % "3.2.3" % Test,
    "org.scalacheck" %% "scalacheck" % "1.15.2" % Test,
    "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test
  ),
)

lazy val metricsCommon = project
  .in(file("metrics-common"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "metrics-common"))

val log4catsVersion = "1.2.1"
val natchezVersion = "0.0.19"
val http4sVersion = "0.21.16"
val circeVersion = "0.13.0"
val slf4jVersion = "1.7.30"
val fs2Version = "2.5.0"

lazy val natchezDatadog = project
  .in(file("natchez-datadog"))
  .enablePlugins(GitVersioning)
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
      "io.circe"     %% "circe-parser"         % circeVersion,
      "org.slf4j"    % "slf4j-api"             % slf4jVersion
)
  )

lazy val natchezSlf4j = project
  .in(file("natchez-slf4j"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-slf4j"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "uk.org.lidalia" % "slf4j-test" % "1.2.0" % Test
    )
  )

lazy val natchezHttp4s = project
  .in(file("natchez-http4s"))
  .dependsOn(natchezTestkit)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-http4s"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core"  % natchezVersion,
      "org.http4s"   %% "http4s-client" % http4sVersion,
      "org.http4s"   %% "http4s-dsl"    % http4sVersion
    )
  )

lazy val natchezLog4Cats = project
  .in(file("natchez-log4cats"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-log4cats"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.typelevel" %% "log4cats-core" % log4catsVersion
    )
  )

lazy val natchezTestkit = project
  .in(file("natchez-testkit"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-testkit"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion
    )
  )

lazy val natchezFs2 = project
  .in(file("natchez-fs2"))
  .dependsOn(natchezTestkit)
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-fs2"))
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "kittens" % "2.2.1",
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "co.fs2" %% "fs2-core" % fs2Version
    )
  )

val silencerVersion = "1.7.1"
val doobieVersion = "0.10.0"
lazy val natchezDoobie = project
  .in(file("natchez-doobie"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-doobie"))
  .settings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "natchez-core" % natchezVersion,
      "org.tpolecat" %% "doobie-core"  % doobieVersion,
      "org.tpolecat" %% "doobie-h2"    % doobieVersion,
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full,
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
    ),
  )

lazy val natchezCombine = project
  .in(file("natchez-combine"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "natchez-combine"))
  .settings(libraryDependencies += "org.tpolecat" %% "natchez-core" % natchezVersion)

lazy val datadogMetrics = project
  .in(file("metrics-datadog"))
  .enablePlugins(GitVersioning)
  .settings(common :+ (name := "datadog-metrics"))
  .dependsOn(metricsCommon)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "claimant" % "0.1.3" % Test,
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
    micrositeName := "effect-utils",
    micrositeBaseUrl := "/effect-utils",
    micrositeDocumentationUrl := "/effect-utils/docs",
    micrositeDescription := "Datadog integrations for functional Scala",
    micrositeImgDirectory := (resourceDirectory in Compile).value / "microsite" / "img",
    micrositePalette := micrositePalette.value ++ Map("brand-primary" -> "#632CA6"),
    mdocVariables := Map("VERSION" -> version.value.takeWhile(_ != '-')),
    micrositePushSiteWith := GHPagesPlugin,
    micrositeGitterChannel := false,
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-blaze-client" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "org.tpolecat" %% "doobie-postgres" % doobieVersion,
      "io.chrisdavenport" %% "log4cats-slf4j" % log4catsVersion
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
    natchezDoobie,
    natchezLog4Cats,
    natchezHttp4s,
    natchezFs2,
    natchezTestkit
  )

Global / onChangedBuildSource := ReloadOnSourceChanges
