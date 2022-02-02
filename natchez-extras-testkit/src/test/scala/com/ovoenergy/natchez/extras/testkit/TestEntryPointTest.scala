package com.ovoenergy.natchez.extras.testkit

import cats.effect.IO
import cats.effect.kernel.Resource.ExitCase.Succeeded
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint.CompletedSpan
import munit.CatsEffectSuite
import natchez.{Kernel, TraceValue}

import java.time.Instant.EPOCH

class TestEntryPointTest extends CatsEffectSuite {

  val testKernel: Kernel =
    Kernel(Map("test" -> "header"))

  test("TestEntryPoint should capture tags sent along with each span") {
    assertIO(
      obtained = TestEntryPoint[IO]
        .flatMap { ep =>
          ep.continue("root-span", testKernel).use { root =>
            root.put("tag" -> "root-span") >> root.span("sub-span").use { span =>
              span.put("tag" -> "sub-span")
            }
          } >> ep.spans
        }
        .map { results =>
          results.map(_.copy(completed = EPOCH))
        },
      returns = List(
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
    )
  }
}
