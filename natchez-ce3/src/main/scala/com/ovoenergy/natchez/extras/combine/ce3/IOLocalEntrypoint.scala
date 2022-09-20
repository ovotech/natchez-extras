package com.ovoenergy.natchez.extras.combine.ce3

import natchez.{EntryPoint, Kernel, Span}
import cats.effect.{IO, IOLocal, Resource}
import cats.implicits._

class IOLocalEntrypoint(private val ep: EntryPoint[IO], private val local: IOLocal[Span[IO]])
    extends EntryPoint[IO] {
  private def localise(span: Span[IO]): Resource[IO, Span[IO]] =
    Resource.make(local.getAndSet(span))(parentSpan => local.set(parentSpan))

  override def root(name: String): Resource[IO, Span[IO]] = ep.root(name).flatTap(localise)

  override def continue(name: String, kernel: Kernel): Resource[IO, Span[IO]] =
    ep.continue(name, kernel).flatTap(localise)

  override def continueOrElseRoot(name: String, kernel: Kernel): Resource[IO, Span[IO]] =
    ep.continueOrElseRoot(name, kernel).flatTap(localise)
}

object IOLocalEntrypoint {
  def createWithRootSpan(
    rootSpanName: String,
    ep: EntryPoint[IO]
  ): Resource[IO, (IOLocalEntrypoint, IOLocalTrace)] =
    ep.root(rootSpanName).evalMap { rootSpan =>
      for {
        local <- IOLocal(rootSpan)
        localEp = new IOLocalEntrypoint(ep, local)
        trace = new IOLocalTrace(local)
      } yield (localEp, trace)
    }
}
