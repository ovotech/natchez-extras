package com.ovoenergy.natchez.extras.datadog.headers

import com.ovoenergy.natchez.extras.datadog.headers.TraceHeaders.{`X-B3-Span-Id`, `X-B3-Trace-Id`}
import munit.FunSuite

class TraceHeadersTest extends FunSuite {

  test("Truncate B3 trace & span headers to their first 64 bits") {
    val longTraceId: String = "463ac35c9f6413ad48485a3953bb6124"
    val traceId = `X-B3-Trace-Id`.header.parse(longTraceId).map(`X-B3-Trace-Id`.header.value)
    val spanId = `X-B3-Span-Id`.header.parse(longTraceId).map(`X-B3-Span-Id`.header.value)
    assertEquals(traceId, Right(longTraceId.take(16)))
    assertEquals(spanId, Right(longTraceId.take(16)))
  }
}
