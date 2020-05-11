package com.ovoenergy.effect

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

import com.ovoenergy.effect.Datadog._
import com.ovoenergy.effect.Events.{AlertType, Event, Priority}
import com.ovoenergy.effect.Metrics.Metric
import org.scalacheck.Gen.mapOf
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.Checkers
import org.typelevel.claimant.Claim

class DatadogTest extends AnyWordSpec with Matchers with Checkers {

  val string: Gen[String] =
    Arbitrary.arbString.arbitrary

  val bytes: Gen[Array[Byte]] =
    string.map(_.getBytes("UTF-8"))

  def makeString(bytes: Array[Byte]) =
    new String(bytes, StandardCharsets.UTF_8)

  val stringTags: Gen[Map[String, String]] =
    mapOf(Gen.zip(string, string))

  implicit val metric: Gen[Metric] =
    for {
      name <- string
      tags <- stringTags
    } yield Metric(name, tags)

  "Datadog serialisation" should {

    "Never submit double underscores to datadog" in {
      check(
        Prop.forAll(string) { str =>
          !makeString(filterName(str)).matches(".*?__.*?")
        }
      )
    }

    "Allow through dots" in {
      check(
        Prop.forAll(Gen.alphaNumStr, Gen.alphaNumStr) {
          case (pref, suf) =>
            makeString(filterName(s"$pref.$suf")) == s"$pref.$suf".dropWhile(!_.isLetter)
        }
      )
    }

    "Allow numbers, hyphens and slashes for tag values" in {
      makeString(filterValue("/12412-1231")) shouldBe "/12412-1231"
    }

    "Generate correct counters and histograms with no tags" in {
      check(
        Prop.forAll(Arbitrary.arbLong.arbitrary) { l =>
          makeString(serialiseCounter(Metric("foo", Map.empty), l)) == s"foo:$l|c" &&
          makeString(serialiseHistogram(Metric("foo", Map.empty), l)) == s"foo:$l|h|@1.0"
        }
      )
    }

    "Generate correct counters & histograms with tags" in {
      check(
        Prop.forAllNoShrink(stringTags.suchThat(_.nonEmpty)) { tags =>
          val exp = tags.map { case (k, v) => s"${makeString(filterName(k))}:${makeString(filterValue(v))}" }.mkString(",")
          Claim(makeString(serialiseHistogram(Metric("foo", tags), 1)) == s"foo:1|h|@1.0|#$exp") &&
          Claim(makeString(serialiseCounter(Metric("foo", tags), 1)) == s"foo:1|c|#$exp")
        }
      )
    }

    "Generate correct events" in {
      val res = serialiseEvent(Event("fooo", "bar", AlertType.Info, Map.empty, Priority.Normal))
      makeString(res) shouldBe "_e{4,3}:fooo|bar|t:info|p:normal"
    }
  }

  "Configuration" should {

    "Not overwrite existing tags" in {
      val config = Config(new InetSocketAddress(0), None, Map("bar" -> "boz"))
      applyConfig(Metric("foo", Map("bar" -> "baz")), config).tags shouldBe Map("bar" -> "baz")
    }

    "Add new tags where they're not specified" in {
      check(
        Prop.forAll(stringTags) { ts =>
          val config = Config(new InetSocketAddress(0), None, ts)
          applyConfig(Metric("foo", Map.empty), config).tags == ts
        }
      )
    }

    "Separate the global prefix from the metric name with a dot" in {
      check(
        Prop.forAll(string) { prefix =>
          val config = Config(new InetSocketAddress(0), Some(prefix), Map("bar" -> "boz"))
          applyConfig(Metric("foo", Map.empty), config).name == s"$prefix.foo"
        }
      )
    }
  }
}
