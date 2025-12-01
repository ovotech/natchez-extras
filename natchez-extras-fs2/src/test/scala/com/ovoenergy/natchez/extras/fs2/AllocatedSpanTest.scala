package com.ovoenergy.natchez.extras.fs2

import cats.effect.{IO, Resource, SyncIO}
import cats.effect.kernel.Resource.ExitCase
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint
import fs2.Stream
import munit.CatsEffectSuite

import java.util.UUID
import scala.concurrent.duration._

class AllocatedSpanTest extends CatsEffectSuite {

  val testEp: SyncIO[FunFixture[TestEntryPoint[IO]]] =
    ResourceFunFixture(Resource.eval(TestEntryPoint.apply[IO]))

  testEp.test("should submit the span even if a pre-submit task fails") { ep =>
    val stream: IO[Unit] =
      Stream
        .emit("foo")
        .covary[IO]
        .through(AllocatedSpan.create(maxOpen = 10)(_ => ep.root("root")))
        .evalTap(_.span.addSubmitTask(IO.raiseError(new Exception)).submit)
        .compile
        .drain

    for {
      _     <- stream.attempt
      spans <- ep.spans
    } yield {
      assertEquals(spans.length, 1)
      assertEquals(spans.maxBy(_.completed).name, "root")
      assertEquals(spans.map(_.exitCase), List.fill(spans.length)(ExitCase.Succeeded))
    }
  }

  testEp.test("Hold the parent span open until it is explicitly submitted") { ep =>
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
      _     <- stream
      spans <- ep.spans
    } yield {
      assertEquals(spans.length, 2)
      assertEquals(spans.minBy(_.completed).name, "foo")
      assertEquals(spans.maxBy(_.completed).name, "root")
      assertEquals(spans.map(_.exitCase), List.fill(spans.length)(ExitCase.Succeeded))
    }
  }

  testEp.test("Cancel the allocated span if the stream dies") { ep =>
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
      _     <- stream.attempt
      spans <- ep.spans
    } yield {
      assertEquals(spans.length, 2)
      val last = spans.maxBy(_.completed)
      val first = spans.minBy(_.completed)
      assertEquals(last.exitCase, ExitCase.Canceled)
      assertEquals(first.exitCase, ExitCase.Succeeded)
    }
  }

  testEp.test("Work with extremely parallel streams") { ep =>
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

    assertIO(
      returns = 100,
      obtained = stream.attempt >> ep.spans.map(_.length)
    )
  }
}
