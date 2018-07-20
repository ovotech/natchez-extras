package com.ovoenergy.effect

import java.time.{Instant, ZonedDateTime, Clock => JavaClock}

import cats.Applicative
import cats.effect.Sync
import cats.syntax.applicative._

/**
  * A referentially transparent clock
  * that returns the time wrapped in an F[_]
  */
trait CurrentTime[F[_]] {
  def now: F[ZonedDateTime]
}

object CurrentTime {

  // used for the underlying instance
  private val javaClock = JavaClock.systemUTC

  def apply[F[_]: CurrentTime]: F[ZonedDateTime] =
    implicitly[CurrentTime[F]].now

  /**
    * Default instance just passes through to Java
    * but keeps RT due to the use of the Sync type class
    */
  def syncClock[F[_]: Sync]: CurrentTime[F] = new CurrentTime[F] {
    override def now = Sync[F].delay(ZonedDateTime.now(javaClock))
  }

  /**
    * Instance that always returns the same time,
    * so only needs F to be an applicative - useful for tests
    */
  def fixed[F[_] : Applicative](instant: Instant): CurrentTime[F] =  new CurrentTime[F] {
    override def now: F[ZonedDateTime] = instant.atZone(javaClock.getZone).pure[F]
  }
}
