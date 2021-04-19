package com.ovoenergy.natchez.extras.fs2

import cats.data.Kleisli
import cats.effect.Sync
import com.ovoenergy.natchez.extras.fs2.AllocatedSpan.Traced
import fs2.Stream
import natchez.Span
import cats.syntax.traverse._

object syntax {

  implicit class StreamOps[F[_]: Sync, A](s: Stream[F, Traced[F, A]]) {
    def evalMapNamed[B](name: String)(op: A => F[B]): Stream[F, Traced[F, B]] =
      s.evalMap(t => t.traverse(a => t.span.span(name).use(_ => op(a))))

    def evalMapTraced[B](name: String)(op: A => Kleisli[F, Span[F], B]): Stream[F, Traced[F, B]] =
      s.evalMap(t => t.traverse(a => t.span.span(name).use(op(a).run)))
  }
}
