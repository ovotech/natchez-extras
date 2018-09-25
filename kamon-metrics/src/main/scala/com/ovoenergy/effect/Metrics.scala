package com.ovoenergy.effect

import cats.effect.Sync
import cats.syntax.functor._
import com.ovoenergy.effect.Metrics.Metric
import kamon.Kamon

import scala.language.higherKinds

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
   * An instance of metrics for a Sync[F] which wraps the underlying calls
   */
  def syncKamonMetrics[F[_]: Sync]: Metrics[F] = new Metrics[F] {
    def counter(metric: Metric): F[Long => F[Unit]] =
      Sync[F].delay(Kamon.metrics.counter(metric.name, metric.tags)).map(
        counter => times => Sync[F].delay(counter.increment(times))
      )

    def histogram(metric: Metric): F[Long => F[Unit]] =
      Sync[F].delay(Kamon.metrics.histogram(metric.name, metric.tags)).map(
        histogram => duration => Sync[F].delay(histogram.record(duration))
      )
  }
}
