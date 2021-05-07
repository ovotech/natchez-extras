package com.ovoenergy.natchez.extras.http4s.server

import cats.Monad
import cats.data.{Kleisli, OptionT}

object syntax {
  /**
   * Given an `A => F[Option[B]]` and an `A => F[B]` run the first function
   * and if it returns F[None] then fall through to the second function.
   * This is useful for composing HttpRoutes[F] with HttpApp[F]
   */
  implicit class KleisliSyntax[F[_]: Monad, A, B](a: Kleisli[OptionT[F, *], A, B]) {
    def fallthroughTo(b: Kleisli[F, A, B]): Kleisli[F, A, B] = Kleisli(r => a.run(r).getOrElseF(b.run(r)))
  }
}
