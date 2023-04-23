package com.ovoenergy.natchez.extras.combine.ce3

import cats.effect.{IO, IOLocal, Resource}
import com.ovoenergy.natchez.extras.combine.ce3.TestSpan.{childSpan, rootSpan}
import munit.CatsEffectSuite
import natchez.{EntryPoint, Kernel, Span}

class IOLocalEntrypointTest extends CatsEffectSuite {
  test(
    "Continuing a trace should set the IOLocal state to the current span"
  ) {
    for {
      local <- IOLocal[Span[IO]](rootSpan)
      ep = new TestEntrypoint {
        override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[IO, Span[IO]] =
          Resource.pure(childSpan)
      }
      epUnderTest = new IOLocalEntrypoint(ep, local)
      spanR = epUnderTest.continue("", Kernel(Map.empty))
      _ <- runAssertions(local, spanR)
    } yield ()
  }

  test(
    "Starting a trace should set the IOLocal state to the current span"
  ) {
    for {
      local <- IOLocal[Span[IO]](rootSpan)
      ep = new TestEntrypoint {
        override def root(name: String, options: Span.Options): Resource[IO, Span[IO]] =
          Resource.pure(childSpan)
      }
      epUnderTest = new IOLocalEntrypoint(ep, local)
      spanR = epUnderTest.root("")
      _ <- runAssertions(local, spanR)
    } yield ()
  }

  test(
    "Starting or continuing a trace should set the IOLocal state to the current span"
  ) {
    for {
      local <- IOLocal[Span[IO]](rootSpan)
      ep = new TestEntrypoint {
        override def continueOrElseRoot(
          name: String,
          kernel: Kernel,
          options: Span.Options
        ): Resource[IO, Span[IO]] =
          Resource.pure(childSpan)
      }
      epUnderTest = new IOLocalEntrypoint(ep, local)
      spanR = epUnderTest.continueOrElseRoot("", Kernel(Map.empty))
      _ <- runAssertions(local, spanR)
    } yield ()
  }

  private def runAssertions(local: IOLocal[Span[IO]], spanR: Resource[IO, Span[IO]]) = {
    local.get.assertEquals(rootSpan) >>
    spanR.use(span =>
      IO(span).assertEquals(childSpan) >>
      local.get.assertEquals(childSpan)
    ) >>
    local.get.assertEquals(rootSpan)
  }

  private class TestEntrypoint extends EntryPoint[IO] {
    override def root(name: String, options: Span.Options): Resource[IO, Span[IO]] = ???

    override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[IO, Span[IO]] = ???

    override def continueOrElseRoot(
      name: String,
      kernel: Kernel,
      options: Span.Options
    ): Resource[IO, Span[IO]] = ???
  }
}
