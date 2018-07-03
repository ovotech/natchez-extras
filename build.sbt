
name := "effect-util"
organization := "com.ovoenergy"
organizationName := "Ovo Energy"
organizationHomepage := Some(url("http://www.ovoenergy.com"))
scalaVersion := "2.12.6"

resolvers += Resolver.sonatypeRepo("releases")

val common = Seq(
  bintrayRepository := "maven-private",
  bintrayOrganization := Some("ovotech"),
  libraryDependencies ++= Seq(
    compilerPlugin("org.spire-math" %% "kind-projector" % "0.9.7"),
    "org.typelevel" %% "cats-effect" % sys.env.getOrElse("CATS_EFFECT_VERSION", "0.10.1"),
    "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  ),
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-encoding",
    "utf8",
    "-feature",
    "-Xfatal-warnings",
    "-language:higherKinds"
  )
)

val logging = project
  .settings(common)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
    )
  )

