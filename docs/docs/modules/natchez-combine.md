---
layout: docs
title: "Natchez Combine"
section: modules
---


# Natchez Combine

`natchez-combine` is a module that allows you to combine two Natchez EntryPoints into one, 
allowing you to send tracing information to more than one destination.

At OVO we use this module to send traces both to Datadog and also to STDOUT (via `natchez-slf4j`) so when
running applications locally we can get a sense of what is going on without having to leave the terminal.

## Example usage:

This example combines `natchez-datadog` and `natchez-slf4j` hence the extra dependencies

```scala
val http4sVersion = "0.21.4"
val effectUtilsVersion = "2.4.0"

libraryDependencies ++= Seq(
  "org.http4s"           %% "http4s-blaze-client" % http4sVersion,
  "com.ovoenergy.effect" %% "natchez-combine"     % effectUtilsVersion,
  "com.ovoenergy.effect" %% "natchez-datadog"     % effectUtilsVersion,
  "com.ovoenergy.effect" %% "natchez-slf4j"       % effectUtilsVersion,
)
```

```scala mdoc
import com.ovoenergy.effect.natchez.Combine
import com.ovoenergy.effect.natchez.Slf4j
import com.ovoenergy.effect.natchez.Datadog
import org.http4s.client.blaze.BlazeClientBuilder
import natchez.EntryPoint
import cats.effect.IO
import cats.effect.Resource
import cats.effect.ExitCode
import cats.effect.IOApp


import scala.concurrent.ExecutionContext.global

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
      httpClient <- BlazeClientBuilder[IO](global).withDefaultSslContext.resource
      entryPoint <- Datadog.entryPoint(httpClient, "service", "resource")
    } yield entryPoint
  
  /**
   * Use natchez-combine to send traces to both SLF4j & Datadog
   * This is what you'll then use for the rest of the application
   */
  val combined: Resource[IO, EntryPoint[IO]] =
    datadog.map { dd => Combine.combine(dd, slf4j) }
    
  def run(args: List[String]): IO[ExitCode] =
    combined.use { _: EntryPoint[IO] => IO.never } // this is the bit you have to do
}
```

