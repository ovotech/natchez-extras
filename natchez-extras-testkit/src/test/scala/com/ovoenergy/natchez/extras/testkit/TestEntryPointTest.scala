package com.ovoenergy.natchez.extras.testkit

import cats.effect.IO
import cats.effect.unsafe.implicits._
import natchez.{Kernel, TraceValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

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
          results.map(s => s.name -> s.tags) shouldBe List(
            "sub-span" -> List("tag" -> ("sub-span": TraceValue)),
            "root-span" -> List("tag" -> ("root-span": TraceValue))
          )
        }
        .unsafeRunSync()
    }
  }
}
