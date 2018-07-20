# effect-util

This repository consists of a number of type classes which add behaviours to a generic `F[_]` type in your cats-effect projects.
Each type class will be deployed to bintray as an independent jar to minimise transitive dependency issues if you only
want to use some of the library.

To create a release, push a tag to master of the format `x.y.z`. See the [semantic versioning guide](https://semver.org/) 
for details of how to choose a version number.

| Module    | Description                                | Artifact    | Version
------------|--------------------------------------------|-------------|--------------------
CurrentTime | wraps `ZonedDateTime.now` within an effect | "com.ovoenergy.effect" % "current-time" |  [ ![Download](https://api.bintray.com/packages/ovotech/maven/current-time/images/download.svg) ](https://bintray.com/ovotech/maven/logging/_latestVersion)
Logging     | wraps logback calls within an effect       | "com.ovoenergy.effect" % "logging"      |  [ ![Download](https://api.bintray.com/packages/ovotech/maven/logging/images/download.svg) ](https://bintray.com/ovotech/maven/logging/_latestVersion)
