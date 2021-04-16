package com.ovoenergy.natchez.extras

import cats.Monad
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import org.typelevel.log4cats.StructuredLogger
import natchez.{Kernel, Span, Trace}

object TracedLogger {

  private def lowercaseHeaders(kernel: Kernel): Map[String, String] =
    kernel.toHeaders.map { case (k, v) => k.toLowerCase -> v }

  /**
   * Kernel to MDC for Datadog
   * See docs at https://docs.datadoghq.com/logs/log_collection/java/?tab=log4j
   */
  private def datadogMdc(kernel: Kernel): Map[String, String] = {
    val headers = lowercaseHeaders(kernel)
    (
      headers.get("x-parent-id").map("dd.span_id" -> _) ++
      headers.get("x-trace-id").map("dd.trace_id" -> _)
    ).toMap
  }

  /**
   * Given a structured logger in some type F which does not have a trace instance
   * lift the logger into a Kleisli (so it then has a trace instance) and wrap it
   */
  def lift[F[_]: Sync](
    logger: StructuredLogger[F],
    kernelMdc: Kernel => Map[String, String] = datadogMdc
  ): StructuredLogger[Kleisli[F, Span[F], *]] =
    apply(logger.mapK[Kleisli[F, Span[F], *]](Kleisli.liftK), kernelMdc)

  /**
   * Given a StructuredLogger wrap it into a new StructuredLogger
   * that extracts a trace_id and span_id from the Natchez Kernel and adds it to the MDC
   */
  def apply[F[_]: Trace: Monad](
    logger: StructuredLogger[F],
    kernelMdc: Kernel => Map[String, String] = datadogMdc
  ): StructuredLogger[F] =
    new StructuredLogger[F] {
      val mdc: F[Map[String, String]] =
        Trace[F].kernel.map(kernelMdc)
      def trace(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.trace(map ++ ctx)(msg))
      def trace(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.trace(map ++ ctx, t)(msg))
      def debug(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.debug(map ++ ctx)(msg))
      def debug(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.debug(map ++ ctx, t)(msg))
      def info(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.info(map ++ ctx)(msg))
      def info(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.info(map ++ ctx, t)(msg))
      def warn(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.warn(map ++ ctx)(msg))
      def warn(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.warn(map ++ ctx, t)(msg))
      def error(ctx: Map[String, String])(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.error(map ++ ctx)(msg))
      def error(ctx: Map[String, String], t: Throwable)(msg: => String): F[Unit] =
        mdc.flatMap(map => logger.error(map ++ ctx, t)(msg))
      def error(t: Throwable)(message: => String): F[Unit] =
        mdc.flatMap(map => logger.error(map, t)(message))
      def warn(t: Throwable)(message: => String): F[Unit] =
        mdc.flatMap(map => logger.warn(map, t)(message))
      def info(t: Throwable)(message: => String): F[Unit] =
        mdc.flatMap(map => logger.info(map, t)(message))
      def debug(t: Throwable)(message: => String): F[Unit] =
        mdc.flatMap(map => logger.debug(map, t)(message))
      def trace(t: Throwable)(message: => String): F[Unit] =
        mdc.flatMap(map => logger.trace(map, t)(message))
      def error(message: => String): F[Unit] =
        mdc.flatMap(map => logger.error(map)(message))
      def warn(message: => String): F[Unit] =
        mdc.flatMap(map => logger.warn(map)(message))
      def info(message: => String): F[Unit] =
        mdc.flatMap(map => logger.info(map)(message))
      def debug(message: => String): F[Unit] =
        mdc.flatMap(map => logger.debug(map)(message))
      def trace(message: => String): F[Unit] =
        mdc.flatMap(map => logger.trace(map)(message))
    }
}
