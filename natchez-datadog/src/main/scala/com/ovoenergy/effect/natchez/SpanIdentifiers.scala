package com.ovoenergy.effect.natchez

import java.util.UUID

import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.apply._
import cats.syntax.functor._
import natchez.Kernel

import scala.util.Try

case class SpanIdentifiers(
  traceId: Long,
  spanId: Long,
  parentId: Option[Long],
  traceToken: String
)

object SpanIdentifiers {

  private def randomAbsLong[F[_]: Sync]: F[Long] =
    Sync[F].delay(scala.util.Random.nextLong().abs)

  private def randomUUID[F[_]: Sync]: F[String] =
    Sync[F].delay(UUID.randomUUID.toString)

  private def stringHeader[F[_]: Sync](kernel: Kernel, name: String): OptionT[F, String] =
    OptionT.fromOption(kernel.toHeaders.get(name))

  private def longHeader[F[_]: Sync](kernel: Kernel, name: String): OptionT[F, Long] =
    stringHeader(kernel, name).subflatMap(h => Try(h.toLong).toOption)

  def create[F[_]: Sync]: F[SpanIdentifiers] =
    (
      randomAbsLong,
      randomAbsLong,
      Sync[F].pure(None),
      randomUUID
    ).mapN(SpanIdentifiers.apply)

  /**
   * Create span identifiers that identify a new child span of the one identified by the provided ids
   * This means the parent ID will be set but a new span ID will be created
   */
  def child[F[_]: Sync](identifiers: SpanIdentifiers): F[SpanIdentifiers] =
    randomAbsLong.map(spanId => identifiers.copy(parentId = Some(identifiers.spanId), spanId = spanId))

  /**
   * Build span identifiers from HTTP headers provided by a client,
   * this will always succeed even if headers are missing because
   * partial data (i.e. just a trace token) is still useful to us
   */
  def fromKernel[F[_]: Sync](rawKernel: Kernel): F[SpanIdentifiers] = {
    val kernel = rawKernel.copy(toHeaders = rawKernel.toHeaders.map { case (k, v) => k.toLowerCase -> v })
    (
      longHeader(kernel, "x-trace-id").getOrElseF(randomAbsLong),
      randomAbsLong,
      longHeader(kernel, "x-parent-id").value,
      stringHeader(kernel, "x-trace-token").getOrElseF(randomUUID),
    ).mapN(SpanIdentifiers.apply)
  }

  def toKernel(ids: SpanIdentifiers): Kernel =
    Kernel(
      Map(
        "X-Parent-Id" -> ids.spanId.toString,
        "X-Trace-Id" -> ids.traceId.toString,
        "X-Trace-Token" -> ids.traceToken
      )
    )
}
