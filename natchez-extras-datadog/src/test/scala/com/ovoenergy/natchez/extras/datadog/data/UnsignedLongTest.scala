package com.ovoenergy.natchez.extras.datadog.data

import com.ovoenergy.natchez.extras.datadog.data.UnsignedLong._
import io.circe.syntax._
import munit.FunSuite

class UnsignedLongTest extends FunSuite {

  val maxValue: UnsignedLong =
    fromString("18446744073709551615", 10).toOption.get

  test("Encode JSON values") {
    assertEquals(encoder(maxValue).toString, "18446744073709551615")
  }

  test("Decode JSON values") {
    val jsonValue = BigInt("18446744073709551615").asJson
    assertEquals(decoder.decodeJson(jsonValue), Right(maxValue))
  }

  test("Decode and Encode decimal-encoded unsigned long values") {
    assertEquals(fromString("18446744073709551615", 10).map(_.toString(10)), Right("18446744073709551615"))
  }

  test("Fail if the long value is out of range") {
    assert(fromString("18446744073709551616", 10).isLeft)
  }

  test("Decode and Encode 64 bit hex-encoded unsigned long values") {
    assertEquals(fromString("a2fb4a1d1a96d312", 16).map(_.toString(16)), Right("a2fb4a1d1a96d312"))
  }

  test("Fail if provided a value over 64 bits in length") {
    assert(fromString("463ac35c9f6413ad48485a3953bb6124", 16).map(_.toString(16)).isLeft)
  }
}
