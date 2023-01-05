---
layout: docs
title: "Natchez Cats Effect 3"
---

# Natchez Cats Effect 3

`natchez-ce3` is a module containing an `IOLocal` based implementation of `Trace` as well as an `Entrypoint` wrapper 
that adds the ability to update the state when starting or continuing a trace.
This removes the need to pass around the underlying `IOLocal` instance and update the state each time a `Span` is started or continued. 

## Installation

In your build.sbt

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "com.ovoenergy" %% "natchez-extras-ce3" % natchezExtrasVersion,
)
```

## Example usage:

This example uses `natchez-extras-slf4j` hence requires the following:

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "com.ovoenergy" %% "natchez-extras-ce3"        % natchezExtrasVersion,
  "com.ovoenergy" %% "natchez-extras-slf4j"          % natchezExtrasVersion
)
```

```scala mdoc
import com.ovoenergy.natchez.extras.combine.ce3.IOLocalEntrypoint
import com.ovoenergy.natchez.extras.slf4j.Slf4j
import natchez.{EntryPoint, Trace}
import cats.effect.IO
import cats.effect.Resource
import cats.effect.ExitCode
import cats.effect.IOApp

object MyTracedApp extends IOApp {

  /**
   * Create a Natchez entrypoint that will log when spans begin and end
   * This is useful when running the application locally
   */
  val slf4j: EntryPoint[IO] =
    Slf4j.entryPoint[IO]

  /**
   * Use natchez-extras-ce3 to propagate `Span`s using `IOLocal` and automatically set them from a wrapped `Entrypoint`.
   * This is what you'll then use for the rest of the application
   */
  val tracing: Resource[IO, (EntryPoint[IO], Trace[IO])] =
    IOLocalEntrypoint.createWithRootSpan("root", slf4j)

  def run(args: List[String]): IO[ExitCode] =
    tracing.use { case ( _: EntryPoint[IO], _: Trace[IO] ) => IO.never } // this is the bit you have to do
}
```
