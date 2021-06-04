---
layout: docs
title: "Natchez Log4Cats"
---

# Natchez Log4Cats

This module provides a wrapper for a `StructuredLogger[F]` that automatically adds a trace ID & span ID to logs
so they will show up alongside traces in Datadog.

For this to work you'll need to configure your logging framework so it sends logs to Datadog. Information about how to do this
can be found in [the Datadog documentation](https://docs.datadoghq.com/logs/log_collection/java/?tab=log4j). While they reccomend
logging to a file we've not experienced any issues sending logs straight to them as described in 
the [agentless logging](https://docs.datadoghq.com/logs/log_collection/java/?tab=log4j#agentless-logging) section.

## Installation

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "com.ovoenergy" %% "natchez-extras-log4cats" % natchezExtrasVersion
)
```

## Usage

This example assumes you've installed the following extra dependency:

```scala
libraryDependencies ++= Seq(
  "org.typelevel" %% "log4cats-slf4j" % "@LOG4CATSVERSION@"
)
```

```scala mdoc
import cats.Functor
import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.ovoenergy.natchez.extras.log4cats.TracedLogger
import com.ovoenergy.natchez.extras.datadog.Datadog
import org.typelevel.log4cats.StructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import natchez.{EntryPoint, Span}
import org.http4s.blaze.client.BlazeClientBuilder
import cats.syntax.functor._

import scala.concurrent.ExecutionContext.global

object NatchezLog4Cats extends IOApp {

  type TracedIO[A] = Kleisli[IO, Span[IO], A]

  /**
   * Create a Natchez entrypoint that will send traces to Datadog
   */
  val datadog: Resource[IO, EntryPoint[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](global).withDefaultSslContext.resource
      entryPoint <- Datadog.entryPoint(httpClient, "example-service", "default-resource")
    } yield entryPoint

  /**
   * Use Log4Cats-Slf4j to create a StructuredLogger we can then pass to TracedLogger
   * to produce a logger that automatically adds the trace ID to the MDC
   */
  val logger: IO[StructuredLogger[TracedIO]] =
    Slf4jLogger.create[IO].map(TracedLogger.lift(_))

  /**
   * The application that uses the logger can depend on it
   * without knowing that it is a TracedLogger
   */
  def application[F[_]: Functor: StructuredLogger]: F[ExitCode] =
    StructuredLogger[F].info("I am running!").as(ExitCode.Success)

  def run(args: List[String]): IO[ExitCode] =
    datadog.use { entryPoint =>
      logger.flatMap { implicit log =>
        entryPoint.root("root_span").use(application[TracedIO].run)
      }
    }
}
```

Assuming your logger is configured correctly you should then be able to see the log entry
alongside the trace in Datadog:

[Datadog trace]({{site.baseurl}}/img/example-logging.png)
