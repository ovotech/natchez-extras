package com.ovoenergy.natchez.extras.datadog

import cats.Monad
import cats.effect.kernel.Async
import cats.effect.kernel.Resource.ExitCase
import cats.effect.std.Queue
import cats.effect.{Clock, Ref, Resource}
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.ovoenergy.natchez.extras.datadog.DatadogSpan.SpanNames
import natchez.TraceValue.{BooleanValue, NumberValue, StringValue}
import natchez.{Kernel, Span, TraceValue}

import java.net.URI
import natchez.Tags

/**
 * Models an in-progress span we'll eventually send to Datadog.
 * We have a trace token as well as a trace ID because Datadog mandates that trace IDs are numeric
 * while we interact with systems that provide non numeric trace tokens
 */
case class DatadogSpan[F[_]: Async](
  names: SpanNames,
  ids: Ref[F, SpanIdentifiers],
  start: Long,
  queue: Queue[F, SubmittableSpan],
  meta: Ref[F, Map[String, TraceValue]]
) extends Span[F] {

  def updateTraceToken(fields: Map[String, TraceValue]): F[Unit] =
    fields
      .get("traceToken")
      .traverse {
        case StringValue(v)  => ids.update(_.copy(traceToken = v))
        case BooleanValue(v) => ids.update(_.copy(traceToken = v.toString))
        case NumberValue(v)  => ids.update(_.copy(traceToken = v.toString))
      }
      .void

  def put(fields: (String, TraceValue)*): F[Unit] =
    meta.update(m => fields.foldLeft(m) { case (m, (k, v)) => m.updated(k, v) }) >>
    updateTraceToken(fields.toMap)

  def span(name: String, options: Span.Options): Resource[F, Span[F]] =
    DatadogSpan.fromParent(name, parent = this).widen

  def kernel: F[Kernel] =
    ids.get.map(SpanIdentifiers.toKernel)

  def traceId: F[Option[String]] =
    ids.get.map(id => Some(id.traceId.toString))

  def spanId: F[Option[String]] =
    ids.get.map(id => Some(id.spanId.toString))

  def traceUri: F[Option[URI]] =
    Monad[F].pure(None)

  override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
    put(Tags.error(true) :: fields.toList: _*)

  override def log(event: String): F[Unit] = put("event" -> TraceValue.StringValue(event))

  override def log(fields: (String, TraceValue)*): F[Unit] = put(fields: _*)
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

  /**
   * Given a span, complete it - this involves turning the span into a `CompletedSpan`
   * which 1:1 matches the Datadog JSON structure before submitting it to a queue of spans
   * we'll eventually submit to the local agent in the background
   */
  def complete[F[_]: Monad: Clock](
    datadogSpan: DatadogSpan[F],
    exitCase: ExitCase
  ): F[Unit] =
    SubmittableSpan
      .fromSpan(datadogSpan, exitCase)
      .flatMap(datadogSpan.queue.offer)

  def create[F[_]: Async](
    queue: Queue[F, SubmittableSpan],
    names: SpanNames,
    meta: Map[String, TraceValue] = Map.empty
  )(identifiers: Ref[F, SpanIdentifiers]): Resource[F, DatadogSpan[F]] =
    Resource.makeCase(
      for {
        start <- Clock[F].realTime
        meta  <- Ref.of(meta)
      } yield DatadogSpan(
        names = names,
        identifiers,
        start = start.toNanos,
        queue = queue,
        meta = meta
      )
    )(complete)

  def fromParent[F[_]: Async](name: String, parent: DatadogSpan[F]): Resource[F, DatadogSpan[F]] =
    for {

      meta  <- Resource.eval(parent.meta.get)
      ids   <- Resource.eval(parent.ids.get.flatMap(SpanIdentifiers.child[F]))
      ref   <- Resource.eval(Ref.of[F, SpanIdentifiers](ids))
      child <- create(parent.queue, SpanNames.withFallback(name, parent.names), meta)(ref)
    } yield child

  def fromKernel[F[_]: Async](
    queue: Queue[F, SubmittableSpan],
    names: SpanNames,
    kernel: Kernel,
    meta: Map[String, TraceValue] = Map.empty
  ): Resource[F, DatadogSpan[F]] =
    Resource
      .eval(
        SpanIdentifiers
          .fromKernel(kernel)
          .flatMap(Ref.of[F, SpanIdentifiers])
      )
      .flatMap(create(queue, names, meta))
}
