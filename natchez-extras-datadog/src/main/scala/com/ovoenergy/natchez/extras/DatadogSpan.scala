package com.ovoenergy.natchez.extras

import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import DatadogSpan.SpanNames
import fs2.concurrent.Queue
import io.circe.generic.extras.Configuration
import natchez.{Kernel, Span, TraceValue}
import cats.syntax.traverse._
import cats.instances.option._
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}

import java.net.URI
import java.util.concurrent.TimeUnit.NANOSECONDS

/**
 * Models an in-progress span we'll eventually send to Datadog.
 * We have a trace token as well as a trace ID because Datadog mandates that trace IDs are numeric
 * while we interact with systems that provide non numeric trace tokens
 */
case class DatadogSpan[F[_]: Sync: Clock](
  names: SpanNames,
  ids: Ref[F, SpanIdentifiers],
  start: Long,
  queue: Queue[F, SubmittableSpan],
  meta: Ref[F, Map[String, TraceValue]],
) extends Span[F] {

  def updateTraceToken(fields: Map[String, TraceValue]): F[Unit] =
    fields.get("traceToken").traverse {
      case StringValue(v) => ids.update(_.copy(traceToken = v))
      case BooleanValue(v) => ids.update(_.copy(traceToken = v.toString))
      case NumberValue(v) => ids.update(_.copy(traceToken = v.toString))
    }.void

  def put(fields: (String, TraceValue)*): F[Unit] =
    meta.update(m => fields.foldLeft(m) { case (m, (k, v)) => m.updated(k, v) }) >>
    updateTraceToken(fields.toMap)

  def span(name: String): Resource[F, Span[F]] =
    DatadogSpan.fromParent(name, parent = this).widen

  def kernel: F[Kernel] =
    ids.get.map(SpanIdentifiers.toKernel)

  def traceId: F[Option[String]] =
    ids.get.map(id => Some(id.traceId.toString))

  def spanId: F[Option[String]] =
    ids.get.map(id => Some(id.spanId.toString))

  def traceUri: F[Option[URI]] =
    Sync[F].pure(None)
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
      string.split(':').toList match {
        case Nil                      => fallback
        case name :: Nil              => SpanNames(name, fallback.service, fallback.resource)
        case name :: resource :: Nil  => SpanNames(name, fallback.service, resource)
        case service :: name :: other => SpanNames(name, service, other.mkString(":"))
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
  )(identifiers: Ref[F, SpanIdentifiers]): Resource[F, DatadogSpan[F]] =
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
      ids   <- Resource.liftF(parent.ids.get.flatMap(SpanIdentifiers.child[F]))
      ref   <- Resource.liftF(Ref.of[F, SpanIdentifiers](ids))
      child <- create(parent.queue, SpanNames.withFallback(name, parent.names), meta)(ref)
    } yield child

  def fromKernel[F[_]: Sync: Clock](
    queue: Queue[F, SubmittableSpan],
    names: SpanNames,
    kernel: Kernel
  ): Resource[F, DatadogSpan[F]] =
    Resource.liftF(
      SpanIdentifiers
        .fromKernel(kernel)
        .flatMap(Ref.of[F, SpanIdentifiers])
    ).flatMap(create(queue, names))
}
