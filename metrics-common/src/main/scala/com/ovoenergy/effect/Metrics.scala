package com.ovoenergy.effect

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
}
