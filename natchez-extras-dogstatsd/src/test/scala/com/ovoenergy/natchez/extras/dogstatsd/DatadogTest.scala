package com.ovoenergy.natchez.extras.dogstatsd

import com.comcast.ip4s.{IpAddress, Port, SocketAddress}
import com.ovoenergy.natchez.extras.dogstatsd.Dogstatsd._
import com.ovoenergy.natchez.extras.dogstatsd.Events.{AlertType, Event, Priority}
import com.ovoenergy.natchez.extras.metrics.Metrics.Metric
import munit.ScalaCheckSuite
import org.scalacheck.Gen.mapOf
import org.scalacheck.{Arbitrary, Gen, Prop}

import java.nio.charset.StandardCharsets.UTF_8

class DatadogTest extends ScalaCheckSuite {

  val string: Gen[String] =
    Arbitrary.arbString.arbitrary

  val bytes: Gen[Array[Byte]] =
    string.map(_.getBytes(UTF_8))

  def makeString(bytes: Array[Byte]) =
    new String(bytes, UTF_8)

  val stringTags: Gen[Map[String, String]] =
    mapOf(Gen.zip(string, string))

  val localAddress: SocketAddress[IpAddress] =
    new SocketAddress(IpAddress.fromString("127.0.0.1").get, Port.fromInt(0).get)

  implicit val metric: Gen[Metric] =
    for {
      name <- string
      tags <- stringTags
    } yield Metric(name, tags)

  test("Serialisation should never submit double underscores to datadog") {
    Prop.forAll(string) { str =>
      !filterName(str).matches(".*?__.*?")
    }
  }

  test("Serialisation should allow through dots") {
    Prop.forAll(Gen.alphaNumStr, Gen.alphaNumStr) { (pref, suf) =>
      filterName(s"$pref.$suf") == s"$pref.$suf".dropWhile(!_.isLetter)
    }
  }

  test("Serialisation should allow numbers, hyphens and slashes for tag values") {
    assertEquals(filterTagValue("/12412-1231"), "/12412-1231")
  }

  test("Serialisation should generate correct counters and histograms with no tags") {
    Prop.forAll(Arbitrary.arbLong.arbitrary) { l =>
      makeString(serialiseCounter(Metric("foo", Map.empty), l)) == s"foo:$l|c" &&
      makeString(serialiseHistogram(Metric("foo", Map.empty), l)) == s"foo:$l|h|@1.0" &&
      makeString(serialiseGauge(Metric("foo", Map.empty), l)) == s"foo:$l|g" &&
      makeString(serialiseDistribution(Metric("foo", Map.empty), l)) == s"foo:$l|d|@1.0"
    }
  }

  test("Serialisation should generate correct counters & histograms with tags") {
    Prop.forAllNoShrink(stringTags.suchThat(_.nonEmpty)) { rawTags =>
      val tags = rawTags.filter { case (k, v) => s"$k$v".length <= 200 }.take(20)
      val exp = tags.map { case (k, v) => s"${filterName(k)}:${filterTagValue(v)}" }.mkString(",")
      assertEquals(makeString(serialiseHistogram(Metric("foo", tags), 1)), s"foo:1|h|@1.0|#$exp")
      assertEquals(makeString(serialiseCounter(Metric("foo", tags), 1)), s"foo:1|c|#$exp")
      assertEquals(makeString(serialiseGauge(Metric("foo", tags), 1)), s"foo:1|g|#$exp")
      assertEquals(makeString(serialiseDistribution(Metric("foo", tags), 1)), s"foo:1|d|@1.0|#$exp")
    }
  }

  test("Serialisation should limit the size of a UDP packet to below the maximum 65535 bytes") {
    Prop.forAllNoShrink(string, stringTags) { (name, tags) =>
      assert(serialiseHistogram(Metric(name, tags), 1).length < 65535)
      assert(serialiseCounter(Metric(name, tags), 1).length < 65535)
      assert(serialiseGauge(Metric(name, tags), 1).length < 65535)
      assert(serialiseDistribution(Metric(name, tags), 1).length < 65535)
    }
  }

  test("Serialisation should generate correct events") {
    val res = serialiseEvent(Event("fooo", "bar\nbaz", AlertType.Info, Map.empty, Priority.Normal))
    assertEquals(makeString(res), "_e{4,8}:fooo|bar\\nbaz|t:info|p:normal")
  }

  test("Config should not overwrite existing tags") {
    val config = Config(localAddress, None, Map("bar" -> "boz"))
    assertEquals(applyConfig(Metric("foo", Map("bar" -> "baz")), config).tags, Map("bar" -> "baz"))
  }

  test("Config should add new tags where they're not specified") {
    Prop.forAll(stringTags) { ts =>
      val config = Config(localAddress, None, ts)
      assertEquals(applyConfig(Metric("foo", Map.empty), config).tags, ts)
    }
  }

  test("Config should separate the global prefix from the metric name with a dot") {
    Prop.forAll(string) { prefix =>
      val config = Config(localAddress, Some(prefix), Map("bar" -> "boz"))
      applyConfig(Metric("foo", Map.empty), config).name == s"$prefix.foo"
    }
  }
}
