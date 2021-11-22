---
layout: docs
title: "Natchez Combine"
---

# Natchez Combine

`natchez-extras-combine` is a module that allows you to combine two Natchez EntryPoints into one,
allowing you to send tracing information to more than one destination.

At OVO we use this module to send traces both to Datadog and also to STDOUT (via `natchez-slf4j`) so when
running applications locally we can get a sense of what is going on without having to leave the terminal.

## Installation

In your build.sbt

(The http4s and datadog dependencies are included for the example below)

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "com.ovoenergy" %% "natchez-extras-combine" % natchezExtrasVersion,
)
```

## Example usage:

This example combines `natchez-extras-datadog` and `natchez-extras-slf4j` hence requires the following:

```scala
val http4sVersion = "@HTTP4SVERSION@"
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "org.http4s"    %% "http4s-blaze-client"           % http4sVersion,
  "com.ovoenergy" %% "natchez-extras-datadog-stable" % natchezExtrasVersion,
  "com.ovoenergy" %% "natchez-extras-combine"        % natchezExtrasVersion,
  "com.ovoenergy" %% "natchez-extras-slf4j"          % natchezExtrasVersion
)
```

```scala mdoc
import com.ovoenergy.natchez.extras.combine.Combine
import com.ovoenergy.natchez.extras.slf4j.Slf4j
import com.ovoenergy.natchez.extras.datadog.Datadog
import org.http4s.blaze.client.BlazeClientBuilder
import natchez.EntryPoint
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
   * Create a Natchez entrypoint that will send traces to Datadog
   */
  val datadog: Resource[IO, EntryPoint[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO].withDefaultSslContext.resource
      entryPoint <- Datadog.entryPoint(httpClient, "service", "resource")
    } yield entryPoint

  /**
   * Use natchez-combine to send traces to both SLF4J & Datadog
   * This is what you'll then use for the rest of the application
   */
  val combined: Resource[IO, EntryPoint[IO]] =
    datadog.map { dd => Combine.combine(dd, slf4j) }

  def run(args: List[String]): IO[ExitCode] =
    combined.use { _: EntryPoint[IO] => IO.never } // this is the bit you have to do
}
```
