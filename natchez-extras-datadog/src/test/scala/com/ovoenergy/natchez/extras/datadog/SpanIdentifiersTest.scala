package com.ovoenergy.natchez.extras.datadog

import cats.effect.IO
import com.ovoenergy.natchez.extras.datadog.SpanIdentifiers._
import com.ovoenergy.natchez.extras.datadog.data.UnsignedLong
import munit.CatsEffectSuite
import natchez.Kernel

class SpanIdentifiersTest extends CatsEffectSuite {

  test("Span identifiers should set IDs correctly when creating child IDs") {
    for {
      parent <- SpanIdentifiers.create[IO]
      child  <- SpanIdentifiers.child[IO](parent)
    } yield {
      assertEquals(parent.parentId, None)
      assertEquals(child.traceId, parent.traceId)
      assertEquals(child.parentId, Some(parent.spanId))
      assertNotEquals(child.spanId, parent.spanId)
    }
  }

  test("Span identifiers should convert to and from a kernel losslessly") {
    for {
      original <- create[IO]
      kernel   <- fromKernel[IO](SpanIdentifiers.toKernel(original))
    } yield {
      assertEquals(kernel.traceId, original.traceId)
      assertEquals(kernel.traceToken, original.traceToken)
      assertEquals(kernel.parentId, Some(original.spanId))
    }
  }

  test("fromKernel should succeed in converting from a kernel even if info is missing") {
    assertIOBoolean(fromKernel[IO](Kernel(Map.empty)).attempt.map(_.isRight))
    assertIO(fromKernel[IO](Kernel(Map("X-Trace-Token" -> "foo"))).map(_.traceToken), "foo")
  }

  test("fromKernel should ignore header case when extracting info") {
    assertIO(fromKernel[IO](Kernel(Map("x-TRACe-tokeN" -> "foo"))).map(_.traceToken), "foo")
  }

  test("toKernel should output hex-encoded B3 Trace IDs alongside decimal encoded Datadog IDs") {
    for {
      ids      <- SpanIdentifiers.create[IO].map(SpanIdentifiers.toKernel)
      ddSpanId <- IO.fromOption(ids.toHeaders.get("X-Trace-Id"))(new Exception("Missing X-Trace-Id"))
      b3SpanId <- IO.fromOption(ids.toHeaders.get("X-B3-Trace-Id"))(new Exception("Missing X-B3-Trace-Id"))
    } yield {
      val ddULong = UnsignedLong.fromString(ddSpanId, 10)
      val b3ULong = UnsignedLong.fromString(b3SpanId, 16)
      assertEquals(ddULong, b3ULong)
    }
  }

  test("toKernel should output hex-encoded B3 Span IDs alongside decimal encoded Datadog Parent IDs") {
    for {
      ids      <- SpanIdentifiers.create[IO].map(SpanIdentifiers.toKernel)
      ddSpanId <- IO.fromOption(ids.toHeaders.get("X-Parent-Id"))(new Exception("Missing X-Parent-Id"))
      b3SpanId <- IO.fromOption(ids.toHeaders.get("X-B3-Span-Id"))(new Exception("Missing X-B3-Span-Id"))
    } yield {
      val ddULong = UnsignedLong.fromString(ddSpanId, 10)
      val b3ULong = UnsignedLong.fromString(b3SpanId, 16)
      assertEquals(ddULong, b3ULong)
    }
  }
}
