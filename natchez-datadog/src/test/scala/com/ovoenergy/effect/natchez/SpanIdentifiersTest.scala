package com.ovoenergy.effect.natchez

import cats.effect.IO
import com.ovoenergy.effect.natchez.SpanIdentifiers._
import natchez.Kernel
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.Checkers

class SpanIdentifiersTest extends AnyWordSpec with Matchers with Checkers {

  "Span identifiers" should {

    "Set IDs correctly when creating child IDs" in {

      val (parent, child) = (
        for {
          parent <- SpanIdentifiers.create[IO]
          child  <- SpanIdentifiers.child[IO](parent)
        } yield parent -> child
      ).unsafeRunSync()

      parent.parentId shouldBe None
      child.traceId shouldBe parent.traceId
      child.parentId shouldBe Some(parent.spanId)
      child.spanId should not be parent.spanId
    }

    "Convert to and from a kernel losslessly" in {
      val (original, kernel) = (
        for {
          ids    <- create[IO]
          kernel <- fromKernel[IO](SpanIdentifiers.toKernel(ids))
        } yield ids -> kernel
      ).unsafeRunSync()

      kernel.traceId shouldBe original.traceId
      kernel.traceToken shouldBe original.traceToken
      kernel.parentId shouldBe Some(original.spanId)
    }
  }

  "Succeed in converting from a kernel even if info is missing" in {
    fromKernel[IO](Kernel(Map.empty)).attempt.unsafeRunSync() should matchPattern { case Right(_) => }
    fromKernel[IO](Kernel(Map("X-Trace-Token" -> "foo"))).unsafeRunSync().traceToken shouldBe "foo"
  }

  "Ignore header case when extracting info" in {
    fromKernel[IO](Kernel(Map("x-TRACe-tokeN" -> "foo"))).unsafeRunSync().traceToken shouldBe "foo"
  }
}
