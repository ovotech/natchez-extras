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

      def put(fields: (String, TraceValue)*): F[Unit] =
        (s1.put(fields *), s2.put(fields *)).tupled.as(())

      def traceId: F[Option[String]] =
        OptionT(s1.traceId).orElseF(s2.traceId).value

      def spanId: F[Option[String]] =
        OptionT(s1.spanId).orElseF(s2.spanId).value

      def traceUri: F[Option[URI]] =
        OptionT(s1.traceUri).orElseF(s2.traceUri).value

      def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
        (s1.attachError(err, fields *), s2.attachError(err, fields *)).tupled.as(())

      def log(event: String): F[Unit] = (s1.log(event), s2.log(event)).tupled.as(())

      def log(fields: (String, TraceValue)*): F[Unit] = (s1.log(fields *), s2.log(fields *)).tupled.as(())

      def span(name: String, options: Span.Options): Resource[F, Span[F]] =
        (s1.span(name, options), s2.span(name, options)).mapN[Span[F]](combineSpan[F])
    }

  def combine[F[_]: Sync](e1: EntryPoint[F], e2: EntryPoint[F]): EntryPoint[F] =
    new EntryPoint[F] {
      override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
        (e1.root(name, options), e2.root(name, options)).mapN(combineSpan[F])

      override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
        (e1.continue(name, kernel, options), e2.continue(name, kernel, options)).mapN(combineSpan[F])

      override def continueOrElseRoot(
        name: String,
        kernel: Kernel,
        options: Span.Options
      ): Resource[F, Span[F]] =
        (e1.continueOrElseRoot(name, kernel, options), e2.continueOrElseRoot(name, kernel, options))
          .mapN(combineSpan[F])
    }
}
