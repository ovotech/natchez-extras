package com.ovoenergy.effect.natchez.http4s.client

import cats.data.Kleisli
import cats.effect.{IO, Timer}
import com.ovoenergy.effect.natchez.TestEntryPoint
import com.ovoenergy.effect.natchez.TestEntryPoint.TestSpan
import com.ovoenergy.effect.natchez.http4s.Configuration
import natchez.{Kernel, Span}
import org.http4s.Request
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.global

class TracedClientTest extends AnyWordSpec with Matchers {

  implicit val timer: Timer[IO] = IO.timer(global)
  val unit: Kleisli[IO, Span[IO], Unit] = Kleisli.pure(())
  val config: Configuration[IO] = Configuration.default[IO]()
  type TraceIO[A] = Kleisli[IO, Span[IO], A]

  "TracedClient" should {

    "Add the kernel to requests" in {

      val requests: List[Request[IO]] = (
        for {
          client <- TestClient[IO]
          ep    <- TestEntryPoint[IO]
          http   = TracedClient(client.client, config)
          kernel = Kernel(Map("X-Trace-Token" -> "token"))
          _      <- ep.continue("bar", kernel).use(http.named("foo").status(Request[TraceIO]()).run)
          reqs   <- client.requests
        } yield reqs
      ).unsafeRunSync()

      requests.forall(_.headers.exists(_.name.value === "X-Trace-Token")) shouldBe true
    }

    "Create a new span for HTTP requests" in {

      val spans: List[TestSpan] = (
        for {
          client <- TestClient[IO]
          ep    <- TestEntryPoint[IO]
          http   = TracedClient(client.client, config)
          _      <- ep.root("root").use(http.named("foo").status(Request[TraceIO]()).run)
          reqs   <- ep.spans
        } yield reqs
      ).unsafeRunSync()

      spans.length shouldBe 2
      spans.head.name shouldBe "foo:http.request:/"
    }
  }
}
