package com.ovoenergy.natchez.extras.datadog.data

import com.ovoenergy.natchez.extras.datadog.data.UnsignedLong.fromString
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UnsignedLongTest extends AnyWordSpec with Matchers {

  "Unsigned long" should {

    "Decode and Encode decimal-encoded unsigned long values" in {
      fromString("18446744073709551615", 10).map(_.toString(10)) shouldBe Right("18446744073709551615")
    }

    "Fail if the long value is out of range" in {
      fromString("18446744073709551616", 10).isLeft shouldBe true
    }

    "Decode and Encode 64 bit hex-encoded unsigned long values" in {
      fromString("a2fb4a1d1a96d312", 16).map(_.toString(16)) shouldBe Right("a2fb4a1d1a96d312")
    }

    "Fail if provided a value over 64 bits in length" in {
      fromString("463ac35c9f6413ad48485a3953bb6124", 16).map(_.toString(16)).isLeft shouldBe true
    }
  }
}
