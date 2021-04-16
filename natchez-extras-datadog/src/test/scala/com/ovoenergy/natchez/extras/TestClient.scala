package com.ovoenergy.natchez.extras

import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, Resource, Sync}
import cats.syntax.apply._
import cats.syntax.flatMap._
import fs2.concurrent.Queue
import org.http4s.client.Client
import org.http4s.{Request, Response}


trait TestClient[F[_]] {
  def respondWith(resp: F[Response[F]]): F[Unit]
  def requests: F[List[Request[F]]]
  def client: Client[F]
}

object TestClient {

  def apply[F[_]: Sync: Concurrent]: F[TestClient[F]]  =
    (
      Ref.of[F, List[Request[F]]](List.empty),
      Queue.unbounded[F, F[Response[F]]]
    ).mapN { case (reqs, resps) =>
      new TestClient[F] {
        def requests: F[List[Request[F]]] =
          reqs.get
        def respondWith(r: F[Response[F]]): F[Unit] =
          resps.enqueue1(r)
        def client: Client[F] =
          Client { r =>
            Resource.liftF(
              reqs.update(_ :+ r) >>
              resps.tryDequeue1.flatMap(r => r.getOrElse(Sync[F].pure(Response[F]())))
            )
          }
      }
    }
}
