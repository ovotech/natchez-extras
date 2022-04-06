package com.ovoenergy.natchez.extras.combine.ce3

import cats.effect.{IO, IOLocal}
import com.ovoenergy.natchez.extras.combine.ce3.TestSpan.{childSpan, rootSpan}
import munit.CatsEffectSuite
import natchez.Span

class IOLocalTraceTest extends CatsEffectSuite {

  test(
    "span should update the IOLocal state for the duration of execution and reset to the parent state afterwards"
  ) {
    for {
      local <- IOLocal[Span[IO]](rootSpan)
      trace = new IOLocalTrace(local)
      _ <- local.get.assertEquals(rootSpan)
      _ <- trace.span("")(local.get).assertEquals(childSpan)
      _ <- local.get.assertEquals(rootSpan)
    } yield ()
  }

}
