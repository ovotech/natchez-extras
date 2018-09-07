package com.ovoenergy.effect

import cats.Applicative
import cats.data.{Writer, WriterT}
import cats.effect.IO
import cats.instances.list._
import cats.syntax.flatMap._
import com.ovoenergy.effect.Logging.{Debug, Error, Info, Log, _}
import org.scalatest.{Inspectors, Matchers, WordSpec}
import cats.syntax.applicative._

class LoggingTest extends WordSpec with Matchers with Inspectors {

  type LogWriter[A] = WriterT[IO, List[Log], A]
  type MdcWriter[A] = WriterT[IO, List[(String, String)], A]
  type TracedLogger[A] = Traced[LogWriter, A]
  type TracedMdc[A] = Traced[MdcWriter, A]
  val token = TraceToken("foo")

  implicit val log: Logging[TracedLogger] =
    tracedInstance[LogWriter](token.pure[LogWriter], (log, _) => WriterT.tell(List(log)))

  def run[A](log: TracedLogger[A]): Writer[List[Log], A] = {
    val (l, a) = log.runEmptyA.run.unsafeRunSync
    Writer(l, a)
  }

  "Tracing" should {

    "Store MDC info" in {
      run(log.putMdc("foo" -> "bar") >> log.mdc).value shouldBe Map("foo" -> "bar")
    }

    "Create a trace token if one is not set then use the same one thereafter" in {
        val result = run(
          for {
            token1 <- log.token
            token2 <- log.token
          } yield List(token1, token2)
        ).value

      result.head.value.nonEmpty shouldBe true
      result.head shouldBe result.tail.head
    }

    "Include MDC info as a parameter to the log function" in {
      val log: Logging[TracedMdc] = tracedInstance(token.pure[MdcWriter], (_, m) => WriterT.tell(m.toList))
      val result = (log.putMdc("foo" -> "bar") >> log.log(Info("blah"))).runEmptyA.written.unsafeRunSync
      result shouldBe List("foo" -> "bar")
    }

    "Overwrite any clashing MDC / logging tags" in {
      val log: Logging[TracedMdc] = tracedInstance(token.pure[MdcWriter], (_, m) => WriterT.tell(m.toList))
      val result = log.putMdc("foo" -> "bar") >> log.log(Info("blah"), Map("foo" -> "baz"))
      result.runEmptyA.written.unsafeRunSync shouldBe List("foo" -> "baz")
    }
  }

  "Logging syntax" should {

    "log before running the actual effect" in {
      val message = Debug("Testing logging")
      val expected = List(message, Info("boo"))
      val toLog = Applicative[TracedLogger].unit
      val events = (toLog >> log.log(Info("boo"))).log(message)
      events.runEmptyA.written.unsafeRunSync shouldEqual expected
    }

    "log exceptions" in {
      val errors = List(
        Error("err"),
        Error(new RuntimeException("ex")),
        Error("another", new RuntimeException("ex2"))
      )

      val toLog = Applicative[TracedLogger].unit
      val events = errors.foldLeft(toLog) { case (unit, err) => unit.log(err) }
      events.runEmptyA.written.unsafeRunSync shouldEqual errors.reverse
    }
  }
}
