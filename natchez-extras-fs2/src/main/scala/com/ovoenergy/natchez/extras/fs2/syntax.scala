package com.ovoenergy.natchez.extras.fs2

import cats.data.Kleisli
import cats.effect.Sync
import com.ovoenergy.natchez.extras.fs2.AllocatedSpan.Traced
import fs2.Stream
import natchez.Span
import cats.syntax.traverse._

object syntax {

  implicit class StreamOps[F[_]: Sync, A](s: Stream[F, Traced[F, A]]) {
    def evalMapNamed[B](name: String, options: Span.Options = Span.Options.Defaults)(
      op: A => F[B]
    ): Stream[F, Traced[F, B]] =
      s.evalMap(t => t.traverse(a => t.span.span(name, options).use(_ => op(a))))

    def evalMapTraced[B](name: String, options: Span.Options = Span.Options.Defaults)(
      op: A => Kleisli[F, Span[F], B]
    ): Stream[F, Traced[F, B]] =
      s.evalMap(t => t.traverse(a => t.span.span(name, options).use(op(a).run)))
  }
}
