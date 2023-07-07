# Natchez Extras

This repository consists of a number of additional integrations for [Natchez](https://github.com/tpolecat/natchez),
primarily to assist with integrating Natchez & Datadog. Separate to the Natchez integrations but included here for simplicity
is a module to send metrics to Datadog over UDP with FS2.

## Cats Effect versions

If you're using Cats Effect 2.x you should use versions 4.x.x of this library,
for Cats Effect 3 use versions 5.0.0 and up.

## http4s dependencies

http4s currently has a stable release (`0.23.*`) and a milestone release (`1.0.0-M*`).
Originally the modules within this library were moved to depend on `1.0.0-M*`
when ported to Cats Effect 3 but the milestone releases don't guarantee 
binary compatibility. (See issue #66).

As such there are two versions of natchez-extras-datadog and natchez-extras-http4s:
- `natchez-extras-datadog-stable` / `natchez-extras-http4s-stable` which depend on http4s `0.23.*`
- `natchez-extras-datadog` / `natchez-extras-http4s` which depend on http4s `1.0.0-M*`

It is recommended that you use the stable variants to avoid binary compatibility issues when
upgrading http4s but the unstable versions will continue to exist just in case they're relied upon. 

## Migration from effect-utils

For historical reasons prior to 4.0.0 this repository was called `effect-utils`.
If you're upgrading your dependencies the renamings are as follows:

```
"com.ovoenergy.effect" % "datadog-metrics"  => "com.ovoenergy" % "natchez-extras-dogstatsd"
"com.ovoenergy.effect" % "natchez-datadog"  => "com.ovoenergy" % "natchez-extras-datadog-stable"
"com.ovoenergy.effect" % "natchez-doobie"   => "com.ovoenergy" % "natchez-extras-doobie"
"com.ovoenergy.effect" % "natchez-slf4j"    => "com.ovoenergy" % "natchez-extras-slf4j"
"com.ovoenergy.effect" % "natchez-combine"  => "com.ovoenergy" % "natchez-extras-combine"
"com.ovoenergy.effect" % "natchez-fs2"      => "com.ovoenergy" % "natchez-extras-fs2"
"com.ovoenergy.effect" % "natchez-testkit"  => "com.ovoenergy" % "natchez-extras-testkit"
"com.ovoenergy.effect" % "natchez-http4s"   => "com.ovoenergy" % "natchez-extras-http4s-stable"
```

Other significant changes are the `Datadog` metrics object being renamed to `Dogstatsd` and the
modules having their code moved into a subpackage under `com.ovoenergy.natchez.extras`, for example:

```scala
import com.ovoenergy.effect.Combine // effect-utils
import com.ovoenergy.natchez.extras.combine.Combine // natchez-extras
```

This is to ensure that each module has an isolated package and so can
define, for example, a `syntax` object without affecting anything else.

## Current modules

### [Dogstatsd](https://ovotech.github.io/natchez-extras/docs/)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-dogstatsd/latest.svg)

This module allows you to send Metrics and Events to the Datadog agent over UDP with FS2.

### [Datadog](https://ovotech.github.io/natchez-extras/docs/natchez-datadog.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-datadog-stable/latest.svg)

This module integrates Natchez with Datadog. It uses HTTP4s and does not depend on the Java Datadog library.

### [Doobie](https://ovotech.github.io/natchez-extras/docs/natchez-doobie.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-doobie/latest.svg)

This module integrates Natchez with Doobie so you can trace which DB queries are being run and for how long.

### [HTTP4S](https://ovotech.github.io/natchez-extras/docs/natchez-http4s.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-http4s-stable/latest.svg)

This module integrates Natchez with the HTTP4S client and provides middleware to trace both inbound and outbound HTTP requests.
It aims to be as configurable as possible so can be configured for use with tracing platforms other than Datadog.

### [Sl4fj](https://ovotech.github.io/natchez-extras/docs/natchez-slf4j.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-slf4j/latest.svg)

This module provides an `Slf4j` integration with Natchez that logs whenever spans get started or completed.
This is mainly useful when running applications locally or integrating with existing logging platforms.

### [Combine](https://ovotech.github.io/natchez-extras/docs/natchez-combine.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-combine/latest.svg)

This module allows two Natchez `EntryPoint`s to be combined so that they'll both be used. For example
if you want to log spans with the above Slf4j integration as well as submitting them to Datadog.

### [FS2](https://ovotech.github.io/natchez-extras/docs/natchez-fs2.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-fs2/latest.svg)

This module provides an `AllocatedSpan` that can be manually submitted, for use in FS2 streams
where the `Resource` based model of Natchez isn't a good fit if you want to have one trace per stream item.

### [Testkit](https://ovotech.github.io/natchez-extras/docs/natchez-testkit.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-testkit/latest.svg)

This module provides a `TestEntrypoint` backed by a `Ref` which can be useful in unit tests.

### [Log4cats](https://ovotech.github.io/natchez-extras/docs/natchez-log4cats.html)
![latest version](https://index.scala-lang.org/ovotech/natchez-extras/natchez-extras-log4cats/latest.svg)

This module provides a `TracedLogger` for `log4cats` that will automatically add trace & span IDs
to your log lines so that they're linked in the Datadog UI.

## Notes for maintainers

To create a release, push a tag to master of the format `x.y.z`. See the [semantic versioning guide](https://semver.org/)
for details of how to choose a version number.


