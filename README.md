# effect-utils

[ ![Download](https://api.bintray.com/packages/ovotech/maven/logging/images/download.svg) ](https://bintray.com/ovotech/maven/logging/_latestVersion)

This repository consists of a number of type classes which add behaviours to a generic `F[_]` type in your cats-effect projects.
Each type class will be deployed to bintray as an independent jar to minimise transitive dependency issues if you only
want to use some of the library.

To create a release, push a tag to master of the format `x.y.z`. See the [semantic versioning guide](https://semver.org/) 
for details of how to choose a version number.

| Module        | Description                                 | Artifact
------------ |-------------------------------------------- |-------------
CurrentTime  | wraps `ZonedDateTime.now` into an `F[_]`    | "com.ovoenergy.effect" % "current-time"
Logging      | wraps logback calls into an `F[_]`          | "com.ovoenergy.effect" % "logging"
Delay        | provides delay for fs2 stream over an `F[_]`| "com.ovoenergy.effect" % "delay"
KamonMetrics | wraps kamon metrics calls into an `F[_]`    | "com.ovoenergy.effect" % "kamon-metrics"
