package com.ovoenergy.natchez.extras.slf4j

import cats.MonadError
import cats.effect.{Resource, Sync}
import natchez.{EntryPoint, Kernel, Span}
import cats.syntax.functor._

object Slf4j {

  def entryPoint[F[_]: Sync]: EntryPoint[F] =
    new EntryPoint[F] {
      def root(name: String, options: Span.Options): Resource[F, Span[F]] =
        Slf4jSpan.create(name).widen

      def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
        Resource.eval(Slf4jSpan.fromKernel(name, kernel).widen).flatMap(identity).widen

      def continueOrElseRoot(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
        Resource
          .eval(
            MonadError[F, Throwable]
              .recover(Slf4jSpan.fromKernel(name, kernel)) { case _ => Slf4jSpan.create(name) }
          )
          .flatMap(identity)
          .widen
    }

}
