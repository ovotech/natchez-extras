# natchez-extras

This repository consists of a number of additional integrations for [Natchez](https://github.com/tpolecat/natchez), 
primarily to assist with integrating Natchez & Datadog. Separate to the Natchez integrations but included here for simplicity
is a module to send metrics to Datadog over UDP with FS2.

## Migration from effect-utils

For historical reasons prior to 4.0.0 this repository was called `effect-utils`.
If you're upgrading your dependencies the renamings are as follows:

```
"com.ovoenergy.effect" % "datadog-metrics"  => "com.ovoenergy" % "natchez-extras-dogstatsd"
"com.ovoenergy.effect" % "natchez-datadog"  => "com.ovoenergy" % "natchez-extras-datadog"
"com.ovoenergy.effect" % "natchez-doobie"   => "com.ovoenergy" % "natchez-extras-doobie"
"com.ovoenergy.effect" % "natchez-slf4j"    => "com.ovoenergy" % "natchez-extras-slf4j"
"com.ovoenergy.effect" % "natchez-combine"  => "com.ovoenergy" % "natchez-extras-combine"
"com.ovoenergy.effect" % "natchez-fs2"      => "com.ovoenergy" % "natchez-extras-fs2"
"com.ovoenergy.effect" % "natchez-testkit"  => "com.ovoenergy" % "natchez-extras-testkit"
```

Other significant changes are the `Datadog` metrics object being renamed to `Dogstatsd` and the
modules having their code moved into a subpackage under `com.ovoenergy.natchez.extras`

i.e.

`com.ovoenergy.effect.Combine` becomes `com.ovoenergy.natchez.extras.combine.Combine`

This is to ensure that the `com.ovoenergy.natchez.extras` namespace won't be polluted by
two modules defining, for example, a `syntax` object.

## Current modules

  Module   | Description                                                                    | Artifact
-----------|--------------------------------------------------------------------------------|-----------------------------------------
Dogstatsd  | Submits metrics to Datadog over UDP with FS2                                   | "com.ovoenergy" % "natchez-extras-dogstatsd"
Datadog    | Integrates [natchez](https://github.com/tpolecat/natchez) with the Datadog APM | "com.ovoenergy" % "natchez-extras-datadog"
Doobie     | Integrates [natchez](https://github.com/tpolecat/natchez) with Doobie          | "com.ovoenergy" % "natchez-extras-doobie"
SLF4J      | Integrates [natchez](https://github.com/tpolecat/natchez) with SLF4J           | "com.ovoenergy" % "natchez-extras-slf4j"
Combine    | Provides a function to combine two Natchez `EntryPoint[F]`s together           | "com.ovoenergy" % "natchez-extras-combine"
FS2        | Provides an `AllocatedSpan` you submit manually for streams                    | "com.ovoenergy" % "natchez-extras-fs2"
Testkit    | Provides a `TestEntrypoint` backed by a `Ref` for unit tests                   | "com.ovoenergy" % "natchez-extras-testkit"

### For maintainers

To create a release, push a tag to master of the format `x.y.z`. See the [semantic versioning guide](https://semver.org/)
for details of how to choose a version number.
