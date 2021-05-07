---
layout: docs
title: "Natchez HTTP4s"
---

# Natchez HTTP4s

`natchez-extras-http4s` provides HTTP4s [Middleware](https://http4s.org/v0.21/middleware/) to trace all HTTP requests.
At the time of writing there is a [PR on Natchez itself](https://github.com/tpolecat/natchez/pull/75) that will provide this functionality.
When it is merged this module will continue to exist but as a wrapper that adds tags used by Datadog.

## Installation

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "com.ovoenergy" %% "natchez-extras-http4s" % natchezExtrasVersion
)
```

## Usage

To use Natchez HTTP4s you create an `HttpApp[Kleisli[F, Span[F], *]]` (i.e. an HttpApp that requires a span to run)
and pass it into `TraceMiddleware` to obtain an `HttpApp[F]` you can then run normally.

```scala mdoc
import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp, Resource, Sync, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.natchez.extras.datadog.Datadog
import com.ovoenergy.natchez.extras.http4s.Configuration
import com.ovoenergy.natchez.extras.http4s.server.TraceMiddleware
import natchez.{EntryPoint, Span, Trace}
import org.http4s.{HttpApp, HttpRoutes}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object NatchezHttp4s extends IOApp {

  /**
   * An example API with a simple GET endpoint
   * and a POST endpoint that does a few sub operations
   */
  def createRoutes[F[_]: Trace: Sync: Timer]: HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._
    HttpRoutes.of {
      case GET -> Root =>
        Ok("Well done")
      case POST -> Root =>
        for {
          _ <- Trace[F].span("operation-1")(Timer[F].sleep(10.millis))
          _ <- Trace[F].span("operation-2")(Timer[F].sleep(50.millis))
          res <- Created("Thanks")
        } yield res
    }
  }

  /**
   * Create a Natchez entrypoint that will send traces to Datadog
   */
  val datadog: Resource[IO, EntryPoint[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](global).withDefaultSslContext.resource
      entryPoint <- Datadog.entryPoint(httpClient, "example-http-api", "default-resource")
    } yield entryPoint


  def run(args: List[String]): IO[ExitCode] =
    datadog.use { entryPoint =>

      /**
       * Our routes need a Trace instance to create spans etc
       * and the only type that has a trace instance is a Kleisli
       */
      type TracedIO[A] = Kleisli[IO, Span[IO], A]
      val tracedRoutes: HttpApp[TracedIO] = createRoutes[TracedIO].orNotFound

      /**
       * We then apply the TraceMiddleware to the routes to obtain an `HttpApp[IO]`.
       * The middleware will create traces for each incoming request.
       */
      val routes: HttpApp[IO] =
        TraceMiddleware[IO](entryPoint, Configuration.default())(tracedRoutes)

      /**
       * We can then serve the routes as normal
       */
      BlazeServerBuilder[IO](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(routes)
        .withoutBanner
        .serve
        .compile
        .lastOrError
    }
}
```

Running the above app and hitting the `POST` endpoint should generate a trace like this:

![datadog trace]({{site.baseurl}}/img/example-http-trace.png)


## Tracing only some routes

Often you don't want to trace all of your routes, for example if you have a healthcheck route
that is polled by a load balancer every few seconds you may wish to exclude it from your traces.

You can do this using `.fallthroughTo` provided in the `syntax` package which allows the combination
of un-traced `HttpRoutes[F]` and the `HttpApp` that the tracing middleware returns:

```scala mdoc
import cats.data.Kleisli
import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.ovoenergy.natchez.extras.datadog.Datadog
import com.ovoenergy.natchez.extras.http4s.Configuration
import com.ovoenergy.natchez.extras.http4s.server.TraceMiddleware
import com.ovoenergy.natchez.extras.http4s.server.syntax.KleisliSyntax
import natchez.{EntryPoint, Span}
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.dsl.io._
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._

import scala.concurrent.ExecutionContext.global

object Main extends IOApp {
  
  type TraceIO[A] = Kleisli[IO, Span[IO], A]
  val conf: Configuration[IO] = Configuration.default()

  val datadog: Resource[IO, EntryPoint[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](global).withDefaultSslContext.resource
      entryPoint <- Datadog.entryPoint(httpClient, "example-http-api", "default-resource")
    } yield entryPoint

  val healthcheck: HttpRoutes[IO] =
    HttpRoutes.of { case GET -> Root / "health" => Ok("healthy") }
  
  val application: HttpRoutes[TraceIO] = 
    HttpRoutes.pure(Response(status = Status.InternalServerError))
   
  def run(args: List[String]): IO[ExitCode] =
    datadog.use { entryPoint =>

      val combinedRoutes: HttpApp[IO] =
        healthcheck.fallthroughTo(TraceMiddleware(entryPoint, conf)(application.orNotFound))
    
      BlazeServerBuilder[IO](global)
        .withHttpApp(combinedRoutes)
        .bindHttp(port = 8080)
        .serve
        .compile
        .lastOrError
    }
}
```

## Configuration

Given that every HTTP API is likely to have different tracing requirements `natchez-http4s` attempts to be as configurable as possible.
The `Configuration` object passed to `TraceMiddleware` defines how to turn an HTTP requests and responses into Natchez tags. By default
it is set up to create tags suitable for Datadog but you can use the helper functions in `Configuration` to create your own configs:

```scala mdoc
import cats.effect.IO
import com.ovoenergy.natchez.extras.http4s.Configuration
import com.ovoenergy.natchez.extras.http4s.Configuration.TagReader._
import natchez.TraceValue.BooleanValue
import cats.syntax.semigroup._

object CustomConfigExample {

  /**
   * Describe what we want to read from request and put as tags into the span.
   * This configuration only adds the url and the method. You can use `|+|` to combine
   * together configurations.
   */
  val customRequestConfig: RequestReader[IO] =
    Configuration.uri[IO]("http_request_url") |+|
    Configuration.method[IO]("http_method")

  /**
   * Describe what to read from the HTTP response generated by the app and put into tags.
   * This configuration won't read anything but will put failed: true if the response is not a 2xx
   */
  val customResponseConfig: ResponseReader[IO] =
    Configuration.ifFailure(Configuration.const("failed", BooleanValue(true)))

  /**
   * The request & response configurations are combined together into this case class
   * which can then be passed to `TraceMiddleware`
   */
  val customConfig: Configuration[IO] =
    Configuration(
      request = customRequestConfig,
      response = customResponseConfig
    )
}
```

