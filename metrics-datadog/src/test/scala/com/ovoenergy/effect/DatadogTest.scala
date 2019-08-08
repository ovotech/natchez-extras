package com.ovoenergy.effect

import java.net.InetSocketAddress

import com.ovoenergy.effect.Datadog._
import com.ovoenergy.effect.Metrics.Metric
import org.scalacheck.Gen.mapOf
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.scalacheck.Checkers

class DatadogTest extends WordSpec with Matchers with Checkers {

  val string: Gen[String] =
    Arbitrary.arbString.arbitrary

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
          !filterChars(str).matches(".*?__.*?")
        }
      )
    }

    "Allow through dots" in {
      check(
        Prop.forAll(Gen.alphaNumStr, Gen.alphaNumStr) { case (pref, suf) =>
          filterChars(s"$pref.$suf") == s"$pref.$suf".dropWhile(!_.isLetter)
        }
      )
    }

    "Generate correct counters and histograms with no tags" in {
      check(
        Prop.forAll(Arbitrary.arbLong.arbitrary) { l =>
          serialiseCounter(Metric("foo", Map.empty), l) == s"foo:$l|c" &&
          serialiseHistogram(Metric("foo", Map.empty), l) == s"foo:$l|h|@1.0"
        }
      )
    }

    "Generate correct counters & histograms with tags" in {
      check(
        Prop.forAll(stringTags.suchThat(_.nonEmpty)) { tags =>
          val exp = tags.map { case (k, v) => s"${filterChars(k)}:${filterChars(v)}"}.mkString(",")
          serialiseHistogram(Metric("foo", tags), 1) == s"foo:1|h|@1.0|#$exp" &&
          serialiseCounter(Metric("foo", tags), 1) == s"foo:1|c|#$exp"
        }
      )
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
