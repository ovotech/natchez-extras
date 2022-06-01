package com.ovoenergy.natchez.extras.http4s.client

import cats.data.Kleisli
import cats.effect.{Resource, Sync}
import cats.syntax.functor._
import cats.~>
import com.ovoenergy.natchez.extras.http4s.Configuration
import com.ovoenergy.natchez.extras.http4s.server.TraceMiddleware.removeNumericPathSegments
import natchez.{Span, Trace}
import org.http4s.Header.ToRaw.keyValuesToRaw
import org.http4s.client.Client

trait TracedClient[F[_]] {
  def named(s: String): Client[F]
}

object TracedClient {

  type Traced[F[_], A] = Kleisli[F, Span[F], A]

  private def dropTracing[F[_]](span: Span[F]): Traced[F, *] ~> F =
    Kleisli.applyK[F, Span[F]](span)

  private def trace[F[_]]: F ~> Traced[F, *] =
    Kleisli.liftK

  def apply[F[_]: Sync](client: Client[F], config: Configuration[F]): TracedClient[Traced[F, *]] =
    name =>
      Client[Traced[F, *]] { req =>
        Resource(
          Trace[Traced[F, *]].span(s"$name:http.request:${removeNumericPathSegments(req.uri)}") {
            for {
              span    <- Kleisli.ask[F, Span[F]]
              headers <- trace(span.kernel.map(_.toHeaders.toSeq))
              withHeader = req.putHeaders(headers.map(keyValuesToRaw): _*).mapK(dropTracing(span))
              reqTags <- trace(config.request.value.run(req.mapK(dropTracing(span))))
              _       <- trace(span.put(reqTags.toSeq: _*))
              tup1    <- client.run(withHeader).mapK(trace[F]).map(_.mapK(trace)).allocated
              (resp, rel) = tup1
              respTags <- trace(config.response.value.run(resp.mapK(dropTracing(span))))
              _        <- trace(span.put(respTags.toSeq: _*))
            } yield resp -> rel
          }
        )
      }
}
