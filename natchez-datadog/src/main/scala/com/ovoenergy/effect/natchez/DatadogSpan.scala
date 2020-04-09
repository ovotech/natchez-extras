package com.ovoenergy.effect.natchez

import java.util.concurrent.TimeUnit.NANOSECONDS

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.effect.natchez.DatadogSpan.SpanNames
import fs2.concurrent.Queue
import io.circe.generic.extras.Configuration
import natchez.{Kernel, Span, TraceValue}

/**
 * Models an in-progress span we'll eventually send to Datadog.
 * We have a trace token as well as a trace ID because Datadog mandates that trace IDs are numeric
 * while we interact with systems that provide non numeric trace tokens
 */
case class DatadogSpan[F[_]: Sync: Clock](
  names: SpanNames,
  ids: SpanIdentifiers,
  start: Long,
  queue: Queue[F, SubmittableSpan],
  meta: Ref[F, Map[String, TraceValue]],
) extends Span[F] {

  def put(fields: (String, TraceValue)*): F[Unit] =
    meta.update(m => fields.foldLeft(m) { case (m, (k, v)) => m.updated(k, v) })

  def span(name: String): Resource[F, Span[F]] =
    DatadogSpan.fromParent(name, parent = this).widen

  def kernel: F[Kernel] =
    Monad[F].pure(SpanIdentifiers.toKernel(ids))
}

object DatadogSpan {

  /**
   * Natchez only allows you to set the span name
   * but we need also a resource + service which can differ by span. As such
   * we allow you to encode this data with an advanced colon based DSL
   */
  case class SpanNames(name: String, service: String, resource: String)

  object SpanNames {

    def withFallback(string: String, fallback: SpanNames): SpanNames =
      string.split(':') match {
        case Array(service, name, resource) => SpanNames(name, service, resource)
        case Array(name, resource)          => SpanNames(name, fallback.service, resource)
        case Array(name)                    => SpanNames(name, fallback.service, fallback.resource)
      }
  }

  implicit val config: Configuration =
    Configuration.default.withSnakeCaseMemberNames

  /**
   * Given a span, complete it - this involves turning the span into a `CompletedSpan`
   * which 1:1 matches the Datadog JSON structure before submitting it to a queue of spans
   * we'll eventually submit to the local agent in the background
   */
  def complete[F[_]: Clock: Sync](
    datadogSpan: DatadogSpan[F],
    exitCase: ExitCase[Throwable]
  ): F[Unit] =
    SubmittableSpan
      .fromSpan(datadogSpan, exitCase)
      .flatMap(datadogSpan.queue.enqueue1)

  def create[F[_]: Sync: Clock](
    queue: Queue[F, SubmittableSpan],
    names: SpanNames,
    meta: Map[String, TraceValue] = Map.empty
  )(identifiers: SpanIdentifiers): Resource[F, DatadogSpan[F]] =
    Resource.makeCase(
      for {
        start <- Clock[F].realTime(NANOSECONDS)
        meta  <- Ref.of(meta)
      } yield
        DatadogSpan(
          names = names,
          identifiers,
          start = start,
          queue = queue,
          meta = meta
        )
    )(complete)

  def fromParent[F[_]: Sync: Clock](name: String, parent: DatadogSpan[F]): Resource[F, DatadogSpan[F]] =
    for {
      meta  <- Resource.liftF(parent.meta.get)
      ids   <- Resource.liftF(SpanIdentifiers.child(parent.ids))
      child <- create(parent.queue, SpanNames.withFallback(name, parent.names), meta)(ids)
    } yield child

  def fromKernel[F[_]: Sync: Clock](
    queue: Queue[F, SubmittableSpan],
    names: SpanNames,
    kernel: Kernel
  ): Resource[F, DatadogSpan[F]] =
    Resource.liftF(SpanIdentifiers.fromKernel(kernel)).flatMap(create(queue, names))
}
