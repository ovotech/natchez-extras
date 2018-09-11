package com.ovoenergy.effect

import cats.effect.IO
import cats.syntax.flatMap._
import com.ovoenergy.effect.Tracing.{TraceIO, TraceToken, _}
import org.scalatest.{Inspectors, Matchers, WordSpec}

class TracingTest extends WordSpec with Matchers with Inspectors {

  val token = TraceToken("foo")
  implicit val trace: Tracing[TraceIO] = Tracing.instance[IO]
  def run[A](t: TraceIO[A]): A = dropTracing[IO].apply[A](t).unsafeRunSync

  "Tracing" should {

    "Store MDC info" in {
      run(putMdc("foo" -> "bar") >> mdc).get("foo") shouldBe Some("bar")
    }

    "Create a trace token if one is not set then use the same one thereafter" in {
        val result = run(
          for {
            token1 <- Tracing.token
            token2 <- Tracing.token
          } yield List(token1, token2)
        )

      result.head.value.nonEmpty shouldBe true
      result.head shouldBe result.tail.head
    }
  }
}
