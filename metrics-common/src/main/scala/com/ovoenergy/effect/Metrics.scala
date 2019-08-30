package com.ovoenergy.effect

import cats.Monad
import cats.syntax.apply._
import cats.syntax.flatMap._
import com.ovoenergy.effect.Metrics.Metric

/**
 * A type class representing the ability to push metrics
 * within some effect type F
 */
trait Metrics[F[_]] {
  def counter(metric: Metric): F[Long => F[Unit]]
  def histogram(metric: Metric): F[Long => F[Unit]]
}

object Metrics {

  case class Metric(name: String, tags: Map[String, String])
  def apply[F[_]: Metrics]: Metrics[F] = implicitly

  /**
   * Combine instances to publish to two different metric providers sequentially,
   * useful when migrating between metrics providers
   */
  def combine[F[_]: Monad](a: Metrics[F], b: Metrics[F]): Metrics[F] =
    new Metrics[F] {
      def counter(m: Metric): F[Long => F[Unit]] =
        (a.counter(m), b.counter(m)).mapN {
          case (a, b) =>
            l =>
              a(l) >> b(l)
        }
      def histogram(m: Metric): F[Long => F[Unit]] =
        (a.histogram(m), b.histogram(m)).mapN {
          case (a, b) =>
            l =>
              a(l) >> b(l)
        }
    }
}
