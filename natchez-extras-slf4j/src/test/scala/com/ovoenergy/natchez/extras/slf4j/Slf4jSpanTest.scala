package com.ovoenergy.natchez.extras.slf4j

import cats.effect.{Concurrent, IO}
import munit.CatsEffectSuite
import natchez.Kernel
import uk.org.lidalia.slf4jtest.{LoggingEvent, TestLoggerFactory}

import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters._

class Slf4jSpanTest extends CatsEffectSuite {

  override def beforeEach(context: BeforeEach): Unit =
    TestLoggerFactory.clearAll()

  val flushLogMessages: IO[List[LoggingEvent]] =
    IO.delay {
      val logs = TestLoggerFactory.getAllLoggingEvents.asScala.toList
      TestLoggerFactory.clearAll()
      logs
    }

  val flushLogs: IO[List[String]] =
    flushLogMessages.map(_.map(_.getMessage))

  test("should log something when the span is created") {
    assertIO(
      obtained = Slf4jSpan.create[IO]("foo").use(_ => flushLogs),
      returns = List("foo started")
    )
  }

  test("should log successes") {
    assertIO(
      obtained = Slf4jSpan.create[IO]("foo").use(_ => flushLogs) >> flushLogs,
      returns = List("foo success")
    )
  }

  test("should Log cancelled tasks") {
    val task: IO[List[LoggingEvent]] = Slf4jSpan.create[IO]("foo").use(_ => flushLogs >> IO.never)
    assertIO(
      returns = List("foo cancelled"),
      obtained = Concurrent[IO].start(task).flatMap { t =>
        IO.sleep(1.milli) >> t.cancel >> flushLogs
      }
    )
  }

  test("Log failed tasks") {
    val explode = flushLogs >> IO.raiseError(new Exception("boo"))
    (Slf4jSpan.create[IO]("foo").use(_ => explode).attempt >> flushLogMessages).map { logs =>
      assertEquals(logs.map(_.getThrowable.get().getMessage), List("boo"))
      assertEquals(logs.map(_.getMessage), List("foo error"))
    }
  }

  test("Include trace tokens") {
    assertIO(
      returns = List.fill(2)(Map("traceToken" -> "bar")),
      obtained = Slf4jSpan.create[IO]("foo", token = Some("bar")).use(IO.pure) >>
        flushLogMessages.map(_.map(_.getMdc.asScala.toMap))
    )
  }

  test("fromKernel should fail if the kernel does not contain a trace token") {
    assertIOBoolean(Slf4jSpan.fromKernel[IO]("foo", Kernel(Map.empty)).attempt.map(_.isLeft))
  }

  test("fromKernel should succeed regardless of the case of the trace token") {
    assertIO(
      returns = "boz",
      obtained = Slf4jSpan
        .fromKernel[IO]("foo", Kernel(Map("x-Trace-TOKEN" -> "boz")))
        .flatMap(_.use(r => IO(r.token)))
    )
  }
}
