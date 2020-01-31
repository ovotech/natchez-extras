package com.ovoenergy.effect

import cats.{Functor, Monad, ~>}
import cats.syntax.apply._
import cats.syntax.flatMap._
import com.ovoenergy.effect.Metrics.Metric
import cats.syntax.functor._

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
   * Transform the effect type of the given Metrics instance with a natural transformation
   */
  def mapK[F[_], G[_]: Functor](metrics: Metrics[F], nt: F ~> G): Metrics[G] =
    new Metrics[G] {
      def counter(metric: Metric): G[Long => G[Unit]] =
        nt(metrics.counter(metric)).map(func => l => nt(func(l)))
      def histogram(metric: Metric): G[Long => G[Unit]] =
        nt(metrics.histogram(metric)).map(func => l => nt(func(l)))
    }

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
