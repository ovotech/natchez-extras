package com.ovoenergy.effect

import cats.data.Kleisli
import cats.effect.Sync
import cats.instances.option._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Functor, Monad}
import io.chrisdavenport.log4cats.StructuredLogger
import natchez.{Kernel, Span, Trace}

object TracedLogger {

  private def lowercaseHeaders(kernel: Kernel): Map[String, String] =
    kernel.toHeaders.map { case (k, v) => k.toLowerCase -> v }

  private def findTraceId(headers: Map[String, String]): Option[String] =
    headers
      .get("x-trace-id")
      .orElse(headers.get("trace_id"))
      .orElse(headers.get("x-b3-traceid"))
      .orElse(headers.get("traceid"))

  private def findSpanId(headers: Map[String, String]): Option[String] =
    headers
      .get("x-parent-id")
      .orElse(headers.get("x-span-id"))
      .orElse(headers.get("span_id"))
      .orElse(headers.get("x-b3-spanid"))
      .orElse(headers.get("spanid"))

  private def mdc[F[_]: Trace: Functor]: F[Map[String, String]] =
    Trace[F].kernel.map { k =>
      (
        findTraceId(lowercaseHeaders(k)),
        findSpanId(lowercaseHeaders(k))
        ).mapN { case (traceId, spanId) =>
        Map(
          "trace_id" -> traceId,
          "span_id" -> spanId
        )
      }.getOrElse(Map.empty)
    }

  /**
   * Given a structured logger in some type F which does not have a trace instance
   * lift the logger into a Kleisli (so it then has a trace instance) and wrap it
   */
  def lift[F[_]: Sync](l: StructuredLogger[F]): StructuredLogger[Kleisli[F, Span[F], *]] =
    apply(l.mapK[Kleisli[F, Span[F], *]](Kleisli.liftK))

  /**
   * Given a StructuredLogger wrap it into a new StructuredLogger
   * that extracts a trace_id and span_id from the Natchez Kernel and adds it to the MDC
   */
  def apply[F[_]: Trace: Monad](l: StructuredLogger[F]): StructuredLogger[F] =
    new StructuredLogger[F] {
      def trace(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.trace(map ++ ctx)(msg))
      def trace(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.trace(map ++ ctx, t)(msg))
      def debug(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.debug(map ++ ctx)(msg))
      def debug(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.debug(map ++ ctx, t)(msg))
      def info(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.info(map ++ ctx)(msg))
      def info(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.info(map ++ ctx, t)(msg))
      def warn(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.warn(map ++ ctx)(msg))
      def warn(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.warn(map ++ ctx, t)(msg))
      def error(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.error(map ++ ctx)(msg))
      def error(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc[F].flatMap(map => l.error(map ++ ctx, t)(msg))
      def error(t: Throwable)(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.error(map, t)(message))
      def warn(t: Throwable)(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.warn(map, t)(message))
      def info(t: Throwable)(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.info(map, t)(message))
      def debug(t: Throwable)(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.debug(map, t)(message))
      def trace(t: Throwable)(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.trace(map, t)(message))
      def error(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.error(map)(message))
      def warn(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.warn(map)(message))
      def info(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.info(map)(message))
      def debug(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.debug(map)(message))
      def trace(message: => String): F[Unit] =
        mdc[F].flatMap(map => l.trace(map)(message))
    }
}
