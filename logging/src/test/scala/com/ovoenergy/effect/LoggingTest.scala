package com.ovoenergy.effect

import cats.Applicative
import cats.data.WriterT
import cats.effect.IO
import cats.instances.list._
import cats.syntax.flatMap._
import com.ovoenergy.effect.Logging._
import org.scalatest.{Inspectors, Matchers, WordSpec}

class LoggingTest extends WordSpec with Matchers with Inspectors {

  type LogWriter[A] = WriterT[IO, List[Log], A]
  implicit val log: Logging[LogWriter] = (l, _) => WriterT.tell(List(l))

  "Logging syntax" should {

    "log before running the actual effect" in {
      val message = Debug("Testing logging")
      val expected = List(message, Info("boo"))
      val toLog = Applicative[LogWriter].unit
      val events = (toLog >> log.log(Info("boo"))).log(message)
      events.written.unsafeRunSync shouldEqual expected
    }

    "log exceptions" in {
      val errors = List(
        Error("err"),
        Error(new RuntimeException("ex")),
        Error("another", new RuntimeException("ex2"))
      )

      val toLog = Applicative[LogWriter].unit
      val events = errors.foldLeft(toLog) { case (unit, err) => unit.log(err) }
      events.written.unsafeRunSync shouldEqual errors.reverse
    }
  }
}
