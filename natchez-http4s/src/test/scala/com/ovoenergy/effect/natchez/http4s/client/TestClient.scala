package com.ovoenergy.effect.natchez.http4s.client

import cats.data.Kleisli
import cats.effect.Sync
import cats.effect.concurrent.Ref
import org.http4s.{Request, Response}
import org.http4s.client.Client
import cats.syntax.functor._

trait TestClient[F[_]] {
  def requests: F[List[Request[F]]]
  def client: Client[F]
}

object TestClient {

  def apply[F[_]: Sync]: F[TestClient[F]] =
    Ref.of[F, List[Request[F]]](List.empty).map { ref =>
      new TestClient[F] {
        def client: Client[F] =
          Client.fromHttpApp[F](Kleisli(r => ref.update(_ :+ r).as(Response[F]())))
        def requests: F[List[Request[F]]] =
          ref.get
      }
    }
}