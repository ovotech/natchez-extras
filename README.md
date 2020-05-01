# effect-utils

[ ![Download](https://api.bintray.com/packages/ovotech/maven/natchez-datadog/images/download.svg) ](https://bintray.com/ovotech/maven/logging/_latestVersion)

This repository consists of a number of type classes which add behaviours to a generic `F[_]` type in your cats-effect projects.
Each type class will be deployed to bintray as an independent jar to minimise transitive dependency issues if you only
want to use some of the library.

To create a release, push a tag to master of the format `x.y.z`. See the [semantic versioning guide](https://semver.org/)
for details of how to choose a version number.

## Current modules

| Module        | Description                                                                    | Artifact
----------------|--------------------------------------------------------------------------------|-----------------------------------------
Datadog Metrics | Submits metrics to Datadog over UDP with FS2                                   | "com.ovoenergy.effect" % "datadog-metrics"
Natchez Datadog | Integrates [natchez](https://github.com/tpolecat/natchez) with the Datadog APM | "com.ovoenergy.effect" % "natchez-datadog"
Natchez Doobie  | Integrates [natchez](https://github.com/tpolecat/natchez) with Doobie          | "com.ovoenergy.effect" % "natchez-doobie"
Natchez SLF4J   | Integrates [natchez](https://github.com/tpolecat/natchez) with SLF4J           | "com.ovoenergy.effect" % "natchez-slf4j"
Natchez Combine | Provides a function to combine two Natchez `EntryPoint[F]`s together           | "com.ovoenergy.effect" % "natchez-combine"
Natchez FS2     | Provides an `AllocatedSpan` you submit manually for streams                    | "com.ovoenergy.effect" % "natchez-fs2"
Natchez Testkit | Provides a `TestEntrypoint` backed by a `Ref` for unit tests                   | "com.ovoenergy.effect" % "natchez-testkit"
