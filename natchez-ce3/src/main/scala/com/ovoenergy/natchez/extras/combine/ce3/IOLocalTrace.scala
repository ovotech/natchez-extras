package com.ovoenergy.natchez.extras.combine.ce3

import natchez.{Kernel, Span, Trace, TraceValue}
import cats.effect.{IO, IOLocal, MonadCancel, Resource}
import cats.~>
import java.net.URI

class IOLocalTrace(private val local: IOLocal[Span[IO]]) extends Trace[IO] {

  override def kernel: IO[Kernel] =
    local.get.flatMap(_.kernel)

  override def put(fields: (String, TraceValue)*): IO[Unit] =
    local.get.flatMap(_.put(fields: _*))

  private def scope[G](t: Span[IO])(f: IO[G]): IO[G] =
    MonadCancel[IO, Throwable].bracket(local.getAndSet(t))(_ => f)(local.set)

  override def span[A](name: String)(k: IO[A]): IO[A] =
    local.get.flatMap {
      _.span(name).use { span =>
        scope(span)(k)
      }
    }

  override def traceId: IO[Option[String]] =
    local.get.flatMap(_.traceId)

  override def traceUri: IO[Option[URI]] =
    local.get.flatMap(_.traceUri)

  override def log(fields: (String, TraceValue)*): IO[Unit] = local.get.flatMap(_.log(fields: _*))

  override def log(event: String): IO[Unit] = local.get.flatMap(_.log(event))

  override def attachError(err: Throwable): IO[Unit] = local.get.flatMap(_.attachError(err))

  override def spanR(name: String, kernel: Option[Kernel]): Resource[IO, IO ~> IO] =
    Resource(
      local.get.flatMap(t =>
        t.span(name, kernel.get).allocated.map {
          case (child, release) =>
            new (IO ~> IO) {
              def apply[A](fa: IO[A]): IO[A] =
                scope(child)(fa)
            } -> release
        }
      )
    )
  override def span[A](name: String, kernel: Kernel)(k: IO[A]): IO[A] =
    local.get.flatMap { span =>
      span.span(name, kernel).use { s =>
        scope(s)(k).onError { case err => s.attachError(err) }
      }
    }
}
