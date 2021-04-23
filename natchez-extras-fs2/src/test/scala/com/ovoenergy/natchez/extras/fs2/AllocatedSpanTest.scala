package com.ovoenergy.natchez.extras.fs2

import cats.effect.IO
import cats.effect.kernel.Resource.ExitCase
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint
import fs2.Stream
import org.scalatest.Inspectors
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.effect.unsafe.implicits.global

import java.util.UUID
import scala.concurrent.duration._

class AllocatedSpanTest extends AnyWordSpec with Matchers {

  val testEp: IO[TestEntryPoint[IO]] =
    TestEntryPoint.apply[IO]


  "AllocatedSpan.create" should {

    "Submit the span even if a pre-submit task fails" in {
      testEp
        .flatMap { ep =>
          val stream: IO[Unit] =
            Stream
              .emit("foo")
              .covary[IO]
              .through(AllocatedSpan.create(maxOpen = 10)(_ => ep.root("root")))
              .evalTap(_.span.addSubmitTask(IO.raiseError(new Exception)).submit)
              .compile
              .drain

          for {
            _ <- stream.attempt
            spans <- ep.spans
          } yield {
            spans.length shouldBe 1
            spans.maxBy(_.completed).name shouldBe "root"
            Inspectors.forAll(spans)(_.exitCase shouldBe ExitCase.Succeeded)
          }
        }
        .unsafeRunSync()
    }


    "Hold the parent span open until it is explicitly submitted" in {
      testEp
        .flatMap { ep =>
          val stream: IO[Unit] =
            Stream
              .emit("foo")
              .covary[IO]
              .through(AllocatedSpan.create(maxOpen = 10)(_ => ep.root("root")))
              .evalTap(_.span.span("foo").use(_ => IO.unit))
              .metered(5.millisecond)
              .evalTap(_.span.submit)
              .compile
              .drain

          for {
            _ <- stream
            spans <- ep.spans
          } yield {
            spans.length shouldBe 2
            spans.minBy(_.completed).name shouldBe "foo"
            spans.maxBy(_.completed).name shouldBe "root"
            Inspectors.forAll(spans)(_.exitCase shouldBe ExitCase.Succeeded)
          }
        }
        .unsafeRunSync()
    }

    "Cancel the allocated span if the stream dies" in {
      testEp
        .flatMap { ep =>
          val stream: IO[Unit] =
            Stream
              .emit("foo")
              .covary[IO]
              .through(AllocatedSpan.create(maxOpen = 10)(_ => ep.root("bar")))
              .evalTap(_.span.span("foo").use(_ => IO.unit))
              .evalTap(_ => IO.raiseError(new Exception("")))
              .compile
              .drain

          for {
            _ <- stream.attempt
            spans <- ep.spans
          } yield {
            spans.length shouldBe 2
            val last = spans.maxBy(_.completed)
            val first = spans.minBy(_.completed)

            last.exitCase shouldBe ExitCase.Canceled
            first.exitCase shouldBe ExitCase.Succeeded
          }
        }
        .unsafeRunSync()
    }

    "Work with extremely parallel streams" in {
      testEp
        .flatMap { ep =>
          val stream: IO[Unit] =
            Stream
              .repeatEval(IO.delay(UUID.randomUUID()))
              .take(50)
              .through(AllocatedSpan.create(maxOpen = 10)(_ => ep.root("root")))
              .parEvalMap(maxConcurrent = 50) { s =>
                s.span.span("foo").use(_ => IO(s))
              }
              .evalMap(_.span.submit)
              .compile
              .drain

          for {
            _ <- stream.attempt
            spans <- ep.spans
          } yield spans.length shouldBe 100
        }
        .unsafeRunSync()
    }
  }
}

