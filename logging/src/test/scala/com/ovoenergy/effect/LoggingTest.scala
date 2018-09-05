package com.ovoenergy.effect

import cats.data.WriterT
import cats.effect.IO
import cats.instances.list._
import cats.syntax.flatMap._
import com.ovoenergy.effect.Logging.{Debug, Error, Info, Log, _}
import org.scalatest.{Matchers, WordSpec}

class LoggingTest extends WordSpec with Matchers {

  type LogWriter[A] = WriterT[IO, List[Log], A]
  implicit val logging: Logging[LogWriter] = log => WriterT.tell(List(log))

  "Logging syntax" should {

    "log before running the actual effect" in {
      val message = Debug("Testing logging")
      val toLog: LogWriter[Unit] = WriterT.liftF(IO.unit)
      val expected = List(message, Info("boo"))
      val events = (toLog >> Logging[LogWriter].log(Info("boo"))).log(message)

      events.written.unsafeRunSync shouldEqual expected
    }

    "log exceptions" in {
      val errors = List(
        Error("err"),
        Error(new RuntimeException("ex")),
        Error("another", new RuntimeException("ex2"))
      )

      val toLog: LogWriter[Unit] = WriterT.liftF(IO.unit)
      val events = errors.foldLeft(toLog) {
        case (log, err) => log.log(err)
      }

      events.written.unsafeRunSync shouldEqual errors.reverse
    }
  }
}
