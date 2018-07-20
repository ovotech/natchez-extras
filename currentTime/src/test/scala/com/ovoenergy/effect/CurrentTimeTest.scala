package com.ovoenergy.effect

import java.time.{Instant, ZoneId, ZonedDateTime}

import cats.Id
import cats.effect.IO
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}

class CurrentTimeTest extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  val z: ZoneId = ZoneId.of("Z")
  implicit val order: Ordering[ZonedDateTime] = _.compareTo(_)

  "CurrentTime instance" should {
    "Return the given time in UTC when using the fixed instance" in {
      forAll { long: Long =>
        val time = Instant.ofEpochMilli(long)
        CurrentTime.fixed[Id](time).now shouldEqual time.atZone(z)
      }
    }
  }

  "CurrentTime sync instance" should {

    "Return a time in UTC" in {
      CurrentTime.syncClock[IO].now.unsafeRunSync.getZone shouldBe z
    }

    "Return increasing times" in {
      val instance = CurrentTime.syncClock[IO]

      val times = for {
        time1 <- instance.now
        time2 <- instance.now
      } yield (time1, time2)

      val (time1, time2) = times.unsafeRunSync
      time1 should be < time2
    }
  }
}
