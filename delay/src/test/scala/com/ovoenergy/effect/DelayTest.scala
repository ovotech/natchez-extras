package com.ovoenergy.effect

import cats.effect.IO
import cats.effect.IO._
import com.ovoenergy.effect.Delay.StreamDelay
import fs2.Stream
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class DelayTest extends WordSpec with Matchers {

  val streamScheduler: StreamDelay[IO] = fs2.Scheduler.apply[IO](2)

  "Delay pipe" should {
    "Delay the execution of the stream" in {

      val streamDelay =
        streamScheduler
          .observe1(
            implicit s =>
              Stream
                .eval(IO(List(1, 2, 3)))
                .flatMap(l => Stream.emits(l))
                .through(Delay[IO].delay(500.millis))
                .compile
                .drain
          )
          .compile
          .drain

      IO.race(IO.sleep(1000.millis), streamDelay).unsafeRunSync shouldEqual Left(())
    }

    "Put the stream to sleep" in {

      val streamDelay =
        streamScheduler
          .observe1(implicit s => Delay[IO].sleep_(2000.millis).compile.drain)
          .compile
          .drain

      IO.race(IO.sleep(1000.millis), streamDelay).unsafeRunSync shouldEqual Left(())
    }
  }
}
