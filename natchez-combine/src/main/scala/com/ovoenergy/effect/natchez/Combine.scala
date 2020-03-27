package com.ovoenergy.effect.natchez

import cats.effect.{Resource, Sync}
import cats.syntax.apply._
import cats.syntax.functor._
import natchez.{EntryPoint, Kernel, Span, TraceValue}

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
