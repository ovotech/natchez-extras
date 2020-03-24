package com.ovoenergy.effect.natchez

import cats.effect.{Concurrent, ContextShift, IO}
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import uk.org.lidalia.slf4jtest.{LoggingEvent, TestLoggerFactory}

import scala.concurrent.ExecutionContext
import cats.syntax.flatMap._
import natchez.Kernel

import scala.util.Try
import scala.collection.mutable
import JavaCollections._

class Slf4jSpanTest extends WordSpec with Matchers with BeforeAndAfterEach {

  implicit val cs: ContextShift[IO] =
    IO.contextShift(ExecutionContext.global)

  override def beforeEach(): Unit =
    TestLoggerFactory.clearAll()

  val flushLogs: IO[List[LoggingEvent]] =
    IO.delay {
      val logs = TestLoggerFactory.getAllLoggingEvents.asScala.toList
      TestLoggerFactory.clearAll()
      logs
    }

  "Slf4j span logging" should {

    "Log something when the span is created" in {
      val started = Slf4jSpan.create[IO]("foo").use(_ => flushLogs).unsafeRunSync
      started.map(_.getMessage) shouldBe List("foo started")
    }

    "Log successes" in {
      Slf4jSpan.create[IO]("foo").use(_ => flushLogs).unsafeRunSync
      flushLogs.unsafeRunSync.map(_.getMessage) shouldBe List("foo success")
    }

    "Log cancelled tasks" in {
      val task: IO[List[LoggingEvent]] = Slf4jSpan.create[IO]("foo").use(_ => flushLogs >> IO.never)
      val result = Concurrent[IO].start(task).flatMap(_.cancel >> flushLogs).unsafeRunSync
      result.map(_.getMessage) shouldBe List("foo cancelled")
    }

    "Log failed tasks" in {
      val explode = flushLogs >> IO.raiseError(new Exception("boo"))
      Try(Slf4jSpan.create[IO]("foo").use(_ => explode).unsafeRunSync)
      val logs = flushLogs.unsafeRunSync
      logs.map(_.getMessage) shouldBe List("foo error")
      logs.map(_.getThrowable.get().getMessage) shouldBe List("boo")
    }

    "Include trace tokens" in {
      Slf4jSpan.create[IO]("foo", token = Some("bar")).use(IO.pure).unsafeRunSync
      flushLogs.unsafeRunSync.map(_.getMdc.asScala) shouldBe List.fill(2)(Map("traceToken" -> "bar"))
    }
  }

  "Slf4j span fromKernel" should {

    "Fail if the kernel does not contain a trace token" in {
      val res = Slf4jSpan.fromKernel[IO]("foo", Kernel(Map.empty)).attempt.unsafeRunSync()
      res should matchPattern { case Left(_) => }
    }

    "Succeed regardless of the case of the trace token" in {
      val res = Slf4jSpan.fromKernel[IO]("foo", Kernel(Map("x-Trace-TOKEN" -> "boz")))
      res.unsafeRunSync.use(s => IO(s.token)).unsafeRunSync shouldBe "boz"
    }
  }

}

/**
  * This is horrible, but it lets us workaround the fact that we need to compile under
  * 2.12 and 2.13 and scala.collection.JavaConverters is deprecated in 2.13 and
  * scala.jdk.ColletionConverters doesn't exist in 2.12...
  */
object JavaCollections {

  implicit class JavaList[A](val list: java.util.List[A]) extends AnyVal {
    def asScala: List[A] = {
      val b = new mutable.ListBuffer[A]
      val it = list.iterator()
      while (it.hasNext()) {
        b += it.next()
      }
      b.toList
    }
  }

  implicit class JavaMap[A, B](val map: java.util.Map[A, B]) extends AnyVal {
    def asScala: Map[A, B] = {
      val b = new mutable.HashMap[A, B]
      val it = map.entrySet().iterator()
      while (it.hasNext()) {
        val e = it.next()
        b.put(e.getKey(), e.getValue())
      }
      b.toMap
    }
  }

}
