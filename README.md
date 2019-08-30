# effect-utils

[ ![Download](https://api.bintray.com/packages/ovotech/maven/logging/images/download.svg) ](https://bintray.com/ovotech/maven/logging/_latestVersion)

This repository consists of a number of type classes which add behaviours to a generic `F[_]` type in your cats-effect projects.
Each type class will be deployed to bintray as an independent jar to minimise transitive dependency issues if you only
want to use some of the library.

To create a release, push a tag to master of the format `x.y.z`. See the [semantic versioning guide](https://semver.org/) 
for details of how to choose a version number.

| Module        | Description                                                                    | Artifact
----------------|--------------------------------------------------------------------------------|-----------------------------------------
Logging         | wraps logback calls into an `F[_]`                                             | "com.ovoenergy.effect" % "logging"
Kamon Metrics   | wraps kamon metrics calls into an `F[_]`                                       | "com.ovoenergy.effect" % "kamon-metrics"
Datadog Metrics | Submits metrics to Datadog over UDP with FS2                                   | "com.ovoenergy.effect" % "datadog-metrics"
Natchez Datadog | Integrates [natchez](https://github.com/tpolecat/natchez) with the Datadog APM | "com.ovoenergy.effect" % "natchez-datadog"
