package com.ovoenergy.natchez.extras.http4s.client

import cats.data.Kleisli
import cats.effect.IO
import com.ovoenergy.natchez.extras.http4s.Configuration
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint
import munit.CatsEffectSuite
import natchez.{Kernel, Span}
import org.http4s.{Header, Request}
import org.typelevel.ci._

class TracedClientTest extends CatsEffectSuite {

  val unit: Kleisli[IO, Span[IO], Unit] = Kleisli.pure(())
  val config: Configuration[IO] = Configuration.default[IO]()
  type TraceIO[A] = Kleisli[IO, Span[IO], A]

  test("Add the kernel to requests") {
    for {
      client <- TestClient[IO]
      ep     <- TestEntryPoint[IO]
      http = TracedClient(client.client, config)
      kernel = Kernel(Map(ci"X-Trace-Token" -> "token"))
      _    <- ep.continue("bar", kernel).use(http.named("foo").status(Request[TraceIO]()).run)
      reqs <- client.requests
    } yield assertEquals(
      obtained = reqs.flatMap(_.headers.headers).filter(_.name == ci"X-Trace-Token"),
      expected = List(Header.Raw(ci"X-Trace-Token", "token"))
    )
  }

  test("Create a new span for HTTP requests") {
    for {
      client <- TestClient[IO]
      ep     <- TestEntryPoint[IO]
      http = TracedClient(client.client, config)
      _     <- ep.root("root").use(http.named("foo").status(Request[TraceIO]()).run)
      spans <- ep.spans
    } yield {
      assertEquals(spans.length, 2)
      assertEquals(spans.head.name, "foo:http.request:/")
    }
  }
}
