package com.ovoenergy.natchez.extras.http4s.server

import cats.data.Kleisli
import cats.effect.Ref
import cats.effect.{IO, Resource}
import cats.{Applicative, Monad}
import com.ovoenergy.natchez.extras.http4s.Configuration
import fs2._
import natchez.TraceValue.{NumberValue, StringValue}
import natchez.{EntryPoint, Kernel, Span, TraceValue}
import org.http4s.Status._
import org.http4s._
import org.http4s.headers._
import org.http4s.syntax.kleisli._
import org.http4s.syntax.literals._
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global
import org.scalatest.wordspec.AnyWordSpec

import java.net.URI

class TraceMiddlewareTest extends AnyWordSpec with Matchers with Inspectors {
  type TraceIO[A] = Kleisli[IO, Span[IO], A]

  val config: Configuration[IO] =
    Configuration.default[IO]()

  def spanMock[F[_]: Applicative](ref: Ref[F, Map[String, TraceValue]]): Span[F] =
    new Span[F] {
      def kernel: F[Kernel] = Applicative[F].pure(Kernel(Map.empty))
      def put(fields: (String, TraceValue)*): F[Unit] = ref.update(_ ++ fields.toMap)
      def span(name: String): Resource[F, Span[F]] = Resource.pure(spanMock[F](ref))
      def traceId: F[Option[String]] = Applicative[F].pure(None)
      def spanId: F[Option[String]] = Applicative[F].pure(None)
      def traceUri: F[Option[URI]] = Applicative[F].pure(None)
    }

  trait TestEntryPoint[F[_]] extends EntryPoint[F] {
    def tags: F[Map[String, TraceValue]]
  }

  def entrypointMock: IO[TestEntryPoint[IO]] =
    Ref.of[IO, Map[String, TraceValue]](Map.empty).map { ref =>
      new TestEntryPoint[IO] {
        def root(name: String): Resource[IO, Span[IO]] =
          Resource.eval(Monad[IO].pure(spanMock(ref)))
        def continue(name: String, kernel: Kernel): Resource[IO, Span[IO]] =
          Resource.eval(Monad[IO].pure(spanMock(ref)))
        def continueOrElseRoot(name: String, kernel: Kernel): Resource[IO, Span[IO]] =
          Resource.eval(Monad[IO].pure(spanMock(ref)))
        def tags: IO[Map[String, TraceValue]] =
          ref.get
      }
    }

  def okService(body: String, headers: Headers = Headers.empty): HttpRoutes[TraceIO] =
    Kleisli.pure(Response[TraceIO](Ok, headers = headers, body = Stream.emits(body.getBytes)))

  def errorService(body: String): HttpRoutes[TraceIO] =
    Kleisli.pure(Response(InternalServerError, body = Stream.emits(body.getBytes)))

  val noContentService: HttpRoutes[TraceIO] =
    Kleisli.pure(Response(InternalServerError))

  "Logging / tracing middleware" should {
    def s(string: String): StringValue = StringValue(string)

    "Add tracing info & log requests + responses" in {
      (
        for {
          entryPoint <- entrypointMock
          svc = TraceMiddleware[IO](entryPoint, config)(okService("ok").orNotFound)
          _   <- svc.run(Request(headers = Headers("X-Trace-Token" -> "foobar")))
          tags <- entryPoint.tags
        } yield tags shouldBe Map(
          "span.type" -> s("web"),
          "http.url" -> s("/"),
          "http.method" -> s("GET"),
          "http.status_code" -> NumberValue(200),
          "http.request.headers" -> s("X-Trace-Token: foobar\n"),
          "http.response.headers" -> s(""),
          "http.url" -> s("/")
        )
      ).unsafeRunSync()
    }

    "Log headers, redacting any sensitive ones" in {

      val responseHeaders = Headers(
        `Set-Cookie`(ResponseCookie("secret", "foo")),
        "X-Polite" -> "come back soon!"
      )

      val requestHeaders = Headers(
        Authorization(BasicCredentials("secret")),
        Cookie(RequestCookie("secret", "secret")),
        `Content-Type`(MediaType.`text/event-stream`)
      )

      (
        for {
          entryPoint <- entrypointMock
          svc = TraceMiddleware[IO](entryPoint, config)(okService("", responseHeaders).orNotFound)
          _   <- svc.run(Request(headers = requestHeaders))
          tags <- entryPoint.tags
        } yield tags shouldBe Map(
          "span.type" -> s("web"),
          "http.url" -> s("/"),
          "http.method" -> s("GET"),
          "http.response.headers" -> s(""),
          "http.status_code" -> NumberValue(200),
          "http.request.headers" -> s(
            """|Authorization: <REDACTED>
               |Cookie: <REDACTED>
               |Content-Type: text/event-stream
               |""".stripMargin
          ),
          "http.response.headers" -> s(
            """|Set-Cookie: <REDACTED>
               |X-Polite: come back soon!
               |""".stripMargin

          )
        )
      ).unsafeRunSync()
    }

    "Include the response body if the response is an error" in {
      (
        for {
          entryPoint <- entrypointMock
          svc = TraceMiddleware[IO](entryPoint, config)(errorService("oh no").orNotFound)
          _   <- svc.run(Request())
          tags <- entryPoint.tags
        } yield tags shouldBe Map(
          "span.type" -> s("web"),
          "http.method" -> s("GET"),
          "http.status_code" -> NumberValue(500),
          "http.response.entity" -> s("oh no"),
          "http.response.headers" -> s(""),
          "http.request.headers" -> s(""),
          "http.url" -> s("/")
        )
      ).unsafeRunSync()
    }

    "Not include the response body if there isn't one on the response" in {
      (
        for {
          entryPoint <- entrypointMock
          svc = TraceMiddleware[IO](entryPoint, config)(noContentService.orNotFound)
          _   <- svc.run(Request())
          tags <- entryPoint.tags
        } yield tags shouldBe Map(
          "span.type" -> s("web"),
          "http.method" -> s("GET"),
          "http.status_code" -> NumberValue(500),
          "http.response.headers" -> s(""),
          "http.request.headers" -> s(""),
          "http.url" -> s("/")
        )
        ).unsafeRunSync()
    }


    "convert URI to a tag-friendly version" in {
      val uri = uri"https://test.com/test/path/ACC-1234/CUST-456/test"
      TraceMiddleware.removeNumericPathSegments(uri) shouldBe "/test/path/_/_/test"
    }
  }
}
