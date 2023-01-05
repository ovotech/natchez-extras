package com.ovoenergy.natchez.extras.combine

import cats.data.OptionT
import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.functor._
import natchez.{EntryPoint, Kernel, Span, TraceValue}

import java.net.URI

/**
 * Given to separate tracing integrations combine them by calling each one of them
 * for all natchez operations. When producing kernels we merge the kernels for maximum compatibility
 */
object Combine {

  private def combineSpan[F[_]: Sync](s1: Span[F], s2: Span[F]): Span[F] =
    new Span[F] {
      def kernel: F[Kernel] =
        (s1.kernel, s2.kernel).mapN { case (k1, k2) => Kernel(k1.toHeaders ++ k2.toHeaders) }

      def span(name: String): Resource[F, Span[F]] =
        (s1.span(name), s2.span(name)).mapN[Span[F]](combineSpan[F])

      def put(fields: (String, TraceValue)*): F[Unit] =
        (s1.put(fields: _*), s2.put(fields: _*)).tupled.as(())

      def traceId: F[Option[String]] =
        OptionT(s1.traceId).orElseF(s2.traceId).value

      def spanId: F[Option[String]] =
        OptionT(s1.spanId).orElseF(s2.spanId).value

      def traceUri: F[Option[URI]] =
        OptionT(s1.traceUri).orElseF(s2.traceUri).value

      override def log(event: String): F[Unit] = log(("event" -> TraceValue.StringValue(event)))

      override def log(fields: (String, TraceValue)*): F[Unit] = put(fields: _*)

      override def attachError(err: Throwable): F[Unit] =
        put(
          Seq(
            ("exit.case" -> TraceValue.StringValue("error")),
            ("exit.error.class" -> TraceValue.StringValue(err.getClass.getName)),
            ("exit.error.message" -> TraceValue.StringValue(err.getMessage)),
            ("exit.error.stackTrace" -> TraceValue
              .StringValue(err.getStackTrace.map(_.toString).mkString("\\n ")))
          ): _*
        )

      override def span(name: String, kernel: Kernel): Resource[F, Span[F]] =
        (s1.span(name, kernel), s2.span(name, kernel)).mapN[Span[F]](combineSpan[F])
    }

  def combine[F[_]: Sync](e1: EntryPoint[F], e2: EntryPoint[F]): EntryPoint[F] =
    new EntryPoint[F] {
      def root(name: String): Resource[F, Span[F]] =
        (e1.root(name), e2.root(name)).mapN(combineSpan[F])

      def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
        (e1.continue(name, kernel), e2.continue(name, kernel)).mapN(combineSpan[F])

      def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
        (e1.continueOrElseRoot(name, kernel), e2.continueOrElseRoot(name, kernel)).mapN(combineSpan[F])
    }
}
