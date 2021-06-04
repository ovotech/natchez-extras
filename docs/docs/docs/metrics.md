---
layout: docs
title: "Metrics"
permalink: docs/
---

# Datadog Metrics

This module provides a `Metrics[F] with Events[F]` trait that lets you send counters, histograms and events to the [Datadog agent](https://docs.datadoghq.com/agent/) over UDP.
Note that this means you are unlikely to receive errors if the Datadog agent isn't reachable!

For more details about counters, histograms & the UDP format see the Datadog [DogStatsD documentation](https://docs.datadoghq.com/developers/dogstatsd/?tab=hostagent).
For more details about events see the [event documentation.](https://docs.datadoghq.com/events/)

## Installation

In your build.sbt

```scala
libraryDependencies += "com.ovoenergy" %% "natchez-extras-dogstatsd" % "@VERSION@"
```

## Example usage

```scala mdoc
import cats.effect.{ExitCode, IO, IOApp}
import com.comcast.ip4s._
import com.ovoenergy.natchez.extras.dogstatsd.Events.{AlertType, Priority}
import com.ovoenergy.natchez.extras.dogstatsd.{Dogstatsd, Events}
import com.ovoenergy.natchez.extras.metrics.Metrics
import com.ovoenergy.natchez.extras.metrics.Metrics.Metric

object MetricApp extends IOApp {

  val metricConfig: Dogstatsd.Config =
    Dogstatsd.Config(

      // adds a prefix to all metrics, e.g. `my_app.metricname`
      metricPrefix = Some("my_app"),

      // the address your Datadog agent is listening on
      agentHost = SocketAddress(ip"127.0.0.1", port"8125"),

      // these tags will be added to all metrics + events
      globalTags = Map("example_tag" -> "example_value")
    )

  val exampleEvent: Events.Event =
    Events.Event(
      title = "Gosh, an event just ocurred!",
      body = "You should investigate this right away",
      alertType = AlertType.Warning,
      priority = Priority.Normal,
      tags = Map.empty
    )

  val exampleCounter: Metric =
    Metric(name = "my_counter", tags = Map.empty)

  val exampleHistogram: Metric =
    Metric(name = "my_histogram", tags = Map.empty)

  def run(args: List[String]): IO[ExitCode] =
    Dogstatsd[IO](metricConfig).use { metrics: Metrics[IO] with Events[IO] =>
      for {
        _ <- metrics.counter(exampleCounter)(1)
        _ <- metrics.histogram(exampleHistogram)(1)
        _ <- metrics.event(exampleEvent)
      } yield ExitCode.Success
    }
}
```
