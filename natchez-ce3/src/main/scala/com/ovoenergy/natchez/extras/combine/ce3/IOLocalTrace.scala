package com.ovoenergy.natchez.extras.combine.ce3

import natchez.{Kernel, Span, Trace, TraceValue}
import cats.effect.{IO, IOLocal, MonadCancel}

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
}
