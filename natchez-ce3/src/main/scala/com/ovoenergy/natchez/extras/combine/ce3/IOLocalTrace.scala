package com.ovoenergy.natchez.extras.combine.ce3

import natchez.{Kernel, Span, Trace, TraceValue}
import cats.effect.{IO, IOLocal, MonadCancel}

import java.net.URI
import cats.effect.Resource
import cats.~>

class IOLocalTrace(private val local: IOLocal[Span[IO]]) extends Trace[IO] {

  override def kernel: IO[Kernel] =
    local.get.flatMap(_.kernel)

  override def put(fields: (String, TraceValue)*): IO[Unit] =
    local.get.flatMap(_.put(fields *))

  private def scope[G](t: Span[IO])(f: IO[G]): IO[G] =
    MonadCancel[IO, Throwable].bracket(local.getAndSet(t))(_ => f)(local.set)

  override def span[A](name: String, options: Span.Options)(k: IO[A]): IO[A] =
    local.get.flatMap {
      _.span(name, options).use { span =>
        scope(span)(k)
      }
    }

  override def traceId: IO[Option[String]] =
    local.get.flatMap(_.traceId)

  override def traceUri: IO[Option[URI]] =
    local.get.flatMap(_.traceUri)

  override def attachError(err: Throwable, fields: (String, TraceValue)*): IO[Unit] =
    local.get.flatMap(_.attachError(err, fields *))
  override def log(event: String): IO[Unit] = local.get.flatMap(_.log(event))
  override def log(fields: (String, TraceValue)*): IO[Unit] = local.get.flatMap(_.log(fields *))
  override def spanR(name: String, options: Span.Options): Resource[IO, IO ~> IO] =
    for {
      parent <- Resource.eval(local.get)
      child  <- parent.span(name, options)
    } yield new (IO ~> IO) {
      def apply[A](fa: IO[A]): IO[A] =
        local.get.flatMap { old =>
          local
            .set(child)
            .bracket(_ => fa.onError(child.attachError(_)))(_ => local.set(old))
        }

    }
}
