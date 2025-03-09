package com.ovoenergy.natchez.extras.datadog

import cats.Monad
import cats.effect.Resource
import cats.effect.kernel.{Concurrent, Ref}
import cats.effect.std.Queue
import cats.syntax.apply._
import cats.syntax.flatMap._
import org.http4s.client.Client
import org.http4s.{Request, Response}

trait TestClient[F[_]] {
  def respondWith(resp: F[Response[F]]): F[Unit]
  def requests: F[List[Request[F]]]
  def client: Client[F]
}

object TestClient {

  def apply[F[_]: Concurrent]: F[TestClient[F]] =
    (
      Ref.of[F, List[Request[F]]](List.empty),
      Queue.unbounded[F, F[Response[F]]]
    ).mapN { case (reqs, resps) =>
      new TestClient[F] {
        def requests: F[List[Request[F]]] =
          reqs.get
        def respondWith(r: F[Response[F]]): F[Unit] =
          resps.offer(r)
        def client: Client[F] =
          Client { r =>
            Resource.eval(
              reqs.update(_ :+ r) >>
                resps.tryTake.flatMap(r => r.getOrElse(Monad[F].pure(Response[F]())))
            )
          }
      }
    }
}
