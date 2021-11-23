package com.ovoenergy.natchez.extras.testkit

import cats.effect.IO
import cats.effect.kernel.Resource.ExitCase.Succeeded
import cats.effect.unsafe.implicits._
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint.CompletedSpan
import natchez.{Kernel, TraceValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant.EPOCH

class TestEntryPointTest extends AnyWordSpec with Matchers {

  val testKernel: Kernel =
    Kernel(Map("test" -> "header"))

  "TestEntryPoint" should {

    "Capture tags sent along with each span" in {
      TestEntryPoint[IO]
        .flatMap { ep =>
          ep.continue("root-span", testKernel).use { root =>
            root.put("tag" -> "root-span") >> root.span("sub-span").use { span =>
              span.put("tag" -> "sub-span")
            }
          } >> ep.spans
        }
        .map { results =>
          results.map(_.copy(completed = EPOCH)) shouldBe List(
            CompletedSpan(
              tags = List("tag" -> ("sub-span": TraceValue)),
              parent = Some("root-span"),
              completed = EPOCH,
              exitCase = Succeeded,
              kernel = testKernel,
              name = "sub-span"
            ),
            CompletedSpan(
              tags = List("tag" -> ("root-span": TraceValue)),
              parent = None,
              completed = EPOCH,
              exitCase = Succeeded,
              kernel = testKernel,
              name = "root-span"
            )
          )
        }
        .unsafeRunSync()
    }
  }
}
