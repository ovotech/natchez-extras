package com.ovoenergy.natchez.extras.http4s.server

import cats.data.Kleisli
import cats.effect.IO
import com.ovoenergy.natchez.extras.http4s.Configuration
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint
import fs2._
import munit.CatsEffectSuite
import natchez.{Span, TraceValue}
import org.http4s.Status._
import org.http4s._
import org.http4s.headers._
import org.http4s.syntax.literals._

class TraceMiddlewareTest extends CatsEffectSuite {
  type TraceIO[A] = Kleisli[IO, Span[IO], A]

  val config: Configuration[IO] =
    Configuration.default[IO]()

  def okService(body: String, headers: Headers = Headers.empty): HttpRoutes[TraceIO] =
    Kleisli.pure(Response[TraceIO](Ok, headers = headers, body = Stream.emits(body.getBytes)))

  def errorService(body: String): HttpRoutes[TraceIO] =
    Kleisli.pure(Response(InternalServerError, body = Stream.emits(body.getBytes)))

  val noContentService: HttpRoutes[TraceIO] =
    Kleisli.pure(Response(InternalServerError))

  test("Add tracing info & log requests + responses") {
    assertIO(
      returns = Map[String, TraceValue](
        "span.type" -> "web",
        "http.url" -> "/",
        "http.method" -> "GET",
        "http.status_code" -> 200,
        "http.request.headers" -> "X-Trace-Token: foobar\n",
        "http.response.headers" -> "",
        "http.url" -> "/"
      ),
      obtained = for {
        entryPoint <- TestEntryPoint[IO]
        svc = TraceMiddleware[IO](entryPoint, config)(okService("ok").orNotFound)
        _     <- svc.run(Request(headers = Headers("X-Trace-Token" -> "foobar")))
        spans <- entryPoint.spans
      } yield spans.head.tags.toMap
    )
  }

  test("Log headers, redacting any sensitive ones") {

    val responseHeaders = Headers(
      `Set-Cookie`(ResponseCookie("secret", "foo")),
      "X-Polite" -> "come back soon!"
    )

    val requestHeaders = Headers(
      Authorization(BasicCredentials("secret")),
      Cookie(RequestCookie("secret", "secret")),
      `Content-Type`(MediaType.`text/event-stream`)
    )

    assertIO(
      returns = Map[String, TraceValue](
        "span.type" -> "web",
        "http.url" -> "/",
        "http.method" -> "GET",
        "http.response.headers" -> "",
        "http.status_code" -> 200,
        "http.request.headers" ->
        """|Authorization: <REDACTED>
           |Cookie: <REDACTED>
           |Content-Type: text/event-stream
           |""".stripMargin,
        "http.response.headers" ->
        """|Set-Cookie: <REDACTED>
           |X-Polite: come back soon!
           |""".stripMargin
      ),
      obtained = for {
        entryPoint <- TestEntryPoint[IO]
        svc = TraceMiddleware[IO](entryPoint, config)(okService("", responseHeaders).orNotFound)
        _     <- svc.run(Request(headers = requestHeaders))
        spans <- entryPoint.spans
      } yield spans.head.tags.toMap
    )
  }

  test("Include the response body if the response is an error") {
    assertIO(
      returns = Map[String, TraceValue](
        "span.type" -> "web",
        "http.method" -> "GET",
        "http.status_code" -> 500,
        "http.response.entity" -> "oh no",
        "http.response.headers" -> "",
        "http.request.headers" -> "",
        "http.url" -> "/"
      ),
      obtained = for {
        entryPoint <- TestEntryPoint[IO]
        svc = TraceMiddleware[IO](entryPoint, config)(errorService("oh no").orNotFound)
        _     <- svc.run(Request())
        spans <- entryPoint.spans
      } yield spans.head.tags.toMap
    )
  }

  test("Not include the response body if there isn't one on the response") {
    assertIO(
      returns = Map[String, TraceValue](
        "span.type" -> "web",
        "http.method" -> "GET",
        "http.status_code" -> 500,
        "http.response.headers" -> "",
        "http.request.headers" -> "",
        "http.url" -> "/"
      ),
      obtained = for {
        entryPoint <- TestEntryPoint[IO]
        svc = TraceMiddleware[IO](entryPoint, config)(noContentService.orNotFound)
        _     <- svc.run(Request())
        spans <- entryPoint.spans
      } yield spans.head.tags.toMap
    )
  }

  test("convert URI to a tag-friendly version") {
    val uri = uri"https://test.com/test/path/ACC-1234/CUST-456/test"
    assertEquals(TraceMiddleware.removeNumericPathSegments(uri), "/test/path/_/_/test")
  }
}
