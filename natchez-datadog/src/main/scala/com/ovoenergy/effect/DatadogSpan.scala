package com.ovoenergy.effect


import java.util.concurrent.TimeUnit.NANOSECONDS

import cats.Monad
import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.effect.DatadogSpan.CompletedSpan
import fs2.concurrent.Queue
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import natchez.{Kernel, Span, TraceValue}

/**
 * Models an in-progress span we'll eventually send to Datadog.
 * We have a trace token as well as a trace ID because Datadog mandates that trace IDs are numeric
 * while we interact with systems that provide non numeric trace tokens
 */
case class DatadogSpan[F[_]: Sync: Clock](
  name: String,
  service: String,
  resource: String,
  ids: SpanIdentifiers,
  start: Long,
  queue: Queue[F, CompletedSpan],
  meta: Ref[F, Map[String, TraceValue]],
) extends Span[F] {

  def put(fields: (String, TraceValue)*): F[Unit] =
    meta.update(m => fields.foldLeft(m) { case (m, (k, v)) => m.updated(k, v)})

  def span(name: String): Resource[F, Span[F]] =
    DatadogSpan.fromParent(name, parent = this).widen

  def kernel: F[Kernel] =
    Monad[F].pure(
      Kernel(
        Map(
          "X-Parent-Id" -> ids.spanId.toString,
          "X-Trace-Id" -> ids.traceId.toString,
          "X-Trace-Token" -> ids.traceToken
        )
      )
    )
}

object DatadogSpan {

  implicit val config: Configuration =
    Configuration.default.withSnakeCaseMemberNames

  case class CompletedSpan(
    traceId: Long,
    spanId: Long,
    name: String,
    service: String,
    resource: String,
    start: Long,
    duration: Long,
    parentId: Option[Long],
    error: Option[Int],
    meta: Map[String, String],
  )

  object CompletedSpan {
    implicit val encode: Encoder[CompletedSpan] = deriveEncoder
  }

  /**
   * Datadog docs:
   * "optional Set this [error] value to 1 to indicate if an error occurred"
   */
  private def isError[A](exitCase: ExitCase[A]): Option[Int] =
    exitCase match {
      case ExitCase.Completed => None
      case ExitCase.Error(_) => Some(1)
      case ExitCase.Canceled => None
    }

  /**
   * Given a span, complete it - this involves turning the span into a `CompletedSpan`
   * which 1:1 matches the Datadog JSON structure before submitting it to a queue of spans
   * we'll eventually submit to the local agent in the background
   */
  def complete[F[_]: Clock: Sync](
    datadogSpan: DatadogSpan[F],
    exitCase: ExitCase[Throwable]
  ): F[Unit] =
    (
      datadogSpan.meta.get,
      Clock[F].realTime(NANOSECONDS)
    ).mapN { case (meta, end) =>
      CompletedSpan(
        traceId = datadogSpan.ids.traceId,
        spanId = datadogSpan.ids.spanId,
        name = datadogSpan.name,
        service = datadogSpan.service,
        resource = datadogSpan.resource,
        start = datadogSpan.start,
        duration = end - datadogSpan.start,
        parentId = datadogSpan.ids.parentId,
        error = isError(exitCase),
        meta = meta.mapValues(_.value.toString).updated("traceToken", datadogSpan.ids.traceToken)
      )
    }.flatMap(datadogSpan.queue.enqueue1)

  /**
   * Datadog identifies traces through a combination of name, service and resource.
   * We set service globally when creating an EntryPoint but we need a bespoke name + resource
   * for every trace & natchez only supports name - as such we assume the name is actually both things
   * split with a colon character
   */
  private def resourceValue(name: String): String =
    name.dropWhile(_ != ':').drop(1)

  private def nameValue(name: String): String =
    name.takeWhile(_ != ':')

  def create[F[_]: Sync: Clock](
    queue: Queue[F, CompletedSpan], name: String, service: String
  )(identifiers: SpanIdentifiers): Resource[F, DatadogSpan[F]] =
    Resource.makeCase(
      for {
        start <- Clock[F].realTime(NANOSECONDS)
        meta <- Ref.of[F, Map[String, TraceValue]](Map.empty)
      } yield DatadogSpan(
        name = nameValue(name),
        service = service,
        resource = resourceValue(name),
        identifiers,
        start = start,
        queue = queue,
        meta = meta
      )
    )(complete)

  def fromParent[F[_]: Sync: Clock](name: String, parent: DatadogSpan[F]): Resource[F, DatadogSpan[F]] =
    Resource.liftF(SpanIdentifiers.child(parent.ids)).flatMap(create(parent.queue, name, parent.service))

  def fromKernel[F[_]: Sync: Clock](
    queue: Queue[F, CompletedSpan],
    name: String,
    service: String,
    kernel: Kernel
  ): Resource[F, DatadogSpan[F]] =
    Resource.liftF(SpanIdentifiers.fromKernel(kernel)).flatMap(create(queue, name, service))
}
