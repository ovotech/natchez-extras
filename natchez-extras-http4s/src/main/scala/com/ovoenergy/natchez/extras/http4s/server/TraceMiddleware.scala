package com.ovoenergy.natchez.extras.http4s.server

import cats.data.Kleisli
import cats.effect.Sync
import cats.~>
import natchez._
import org.http4s._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.natchez.extras.http4s.Configuration

object TraceMiddleware {

  /**
   * Given a URI produce its path but with any segments containing only two or more numbers
   * replace with an underscore. This is to stop things like Account IDs showing up in URLs
   */
  def removeNumericPathSegments(uri: Uri): String =
    uri.path.replaceAll("(^|/)[^/]*[0-9]{2,}[^/]*", "$1_")

  private def runTracing[F[_]](s: Span[F]): Kleisli[F, Span[F], *] ~> F =
    new (Kleisli[F, Span[F], *] ~> F) { def apply[A](a: Kleisli[F, Span[F], A]): F[A] = a.run(s) }

  /**
   * Wrap the given traced HTTP4s routes and upon receiving requests
   * create a new trace and pass the root span to the routes
   */
  def apply[F[_]](
    entryPoint: EntryPoint[F],
    configuration: Configuration[F]
  )(
    service: HttpApp[Kleisli[F, Span[F], *]]
  )(implicit F: Sync[F]): HttpApp[F] =
    Kleisli { r =>
      val spanName = s"http.request:${removeNumericPathSegments(r.uri)}"
      val kernel = Kernel(r.headers.toList.map(h => h.name.toString -> h.value).toMap)
      val traceRequest = r.mapK(Kleisli.liftK[F, Span[F]])

      entryPoint
        .continueOrElseRoot(spanName, kernel)
        .use { span =>
          for {
            reqTags    <- configuration.request.value.run(r)
            _          <- span.put(reqTags.toSeq: _*)
            tracedResp <- service.run(traceRequest).run(span)
            response = tracedResp.mapK(runTracing(span))
            respTags <- configuration.response.value.run(response)
            _        <- span.put(respTags.toSeq: _*)
          } yield response
        }
    }
}
