package com.ovoenergy.effect

import cats.FlatMap
import cats.effect.Sync
import com.ovoenergy.effect.Metrics.Metric
import kamon.Kamon

import scala.language.higherKinds

/**
 * A type class representing the ability to push metrics
 * within some effect type F
 */
trait Metrics[F[_]] {
  def counter(metric: Metric): F[Long => F[Unit]]
}

object Metrics {

  case class Metric(name: String, tags: Map[String, String])

  def apply[F[_]: Metrics]: Metrics[F] = implicitly

  /**
   * An instance of metrics for a Sync[F] which wraps the underlying calls
   */
  def syncKamonMetrics[F[_]: Sync: FlatMap]: Metrics[F] =
    metric =>
      FlatMap[F].map(Sync[F].delay(Kamon.metrics.counter(metric.name, metric.tags)))(
        counter => times => Sync[F].delay(counter.increment(times))
    )
}
