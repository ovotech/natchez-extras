package com.ovoenergy.natchez.extras.combine.ce3

import cats.effect.{IO, Resource}
import natchez.{Kernel, Span, TraceValue}

import java.net.URI

protected[ce3] abstract class TestSpan(val name: String) extends Span[IO] {
  override def put(fields: (String, TraceValue)*): IO[Unit] = ???
  override def kernel: IO[Kernel] = ???
  override def span(name: String, options: Span.Options): Resource[IO, Span[IO]] = ???
  override def traceId: IO[Option[String]] = ???
  override def spanId: IO[Option[String]] = ???
  override def traceUri: IO[Option[URI]] = ???
  override def attachError(err: Throwable, fields: (String, TraceValue)*): IO[Unit] = ???
  override def log(event: String): IO[Unit] = ???
  override def log(fields: (String, TraceValue)*): IO[Unit] = ???
}

protected[ce3] object TestSpan {
  val childSpan = new TestSpan("child") {}
  val rootSpan = new TestSpan("root") {
    override def span(name: String, options: Span.Options): Resource[IO, Span[IO]] = Resource.pure(childSpan)
  }
}
