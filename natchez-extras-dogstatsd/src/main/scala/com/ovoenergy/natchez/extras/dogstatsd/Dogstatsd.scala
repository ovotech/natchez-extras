package com.ovoenergy.natchez.extras.dogstatsd

import cats.effect.Resource
import com.comcast.ip4s.{IpAddress, SocketAddress}
import com.ovoenergy.natchez.extras.dogstatsd.Events.Event
import com.ovoenergy.natchez.extras.metrics.Metrics
import com.ovoenergy.natchez.extras.metrics.Metrics.Metric
import fs2.Chunk.array
import fs2.io.net.{Datagram, DatagramSocket, Network}

import java.nio.charset.StandardCharsets.UTF_8

object Dogstatsd {

  // just for readability
  type UTF8Bytes = Array[Byte]

  private implicit class StringOps(s: String) {
    def utf8Bytes: UTF8Bytes = s.getBytes(UTF_8)
  }

  private implicit class ByteArrayOps(bs: UTF8Bytes) {
    def sep(char: Char)(other: UTF8Bytes): UTF8Bytes = (bs :+ char.toByte) ++ other
    def colon: UTF8Bytes => UTF8Bytes = sep(':')
    def pipe: UTF8Bytes => UTF8Bytes = sep('|')
  }

  case class Config(
    agentHost: SocketAddress[IpAddress],
    metricPrefix: Option[String],
    globalTags: Map[String, String]
  )

  /**
   * https://docs.datadoghq.com/developers/metrics/#naming-custom-metrics
   */
  val maxMetricNameLength = 200

  /**
   * https://docs.datadoghq.com/tagging/
   */
  val maxTagLength = 200

  /**
   * Apparently the maximum UDP packet size is 65535 bytes (at the absolute maximum) and we have many
   * different strings in a packet so we only allow each one to be 2k chars
   */
  val maxStringLength = 2000

  /**
   * A basic sanity check for the maximum number of tags to send to Datadog 20 x 200 (max key + value size) =
   * 4k chars so seems okay
   */
  val maximumTagCount = 20

  /**
   * Datadog enforces all metrics must start with a letter and not contain any chars other than letters,
   * numbers and underscores
   */
  private[extras] def filterName(s: String): String =
    s.dropWhile(!_.isLetter).replaceAll("[^A-Za-z0-9.]+", "_").take(maxMetricNameLength)

  /**
   * More lenient filtering for tag values, we allow alpha numeric characters, slashes, hyphens and numbers
   */
  private[extras] def filterTagValue(s: String): String =
    s.replaceAll("[^A-Za-z0-9./\\-]+", "_")

  /**
   * Datadog receives events as binary byte arrays then converts them into UTF-8 strings
   * https://github.com/DataDog/datadog-agent/blob/21a80ab80de389adb9c74e3e9a7162f83fda3e0c/pkg/dogstatsd/parse_events.go#L62
   * As such we obtain the byte values from the string in UTF-8
   */
  private[extras] def filterEventText(s: String): UTF8Bytes =
    s.take(maxStringLength).replaceAll("[\\r\\n]", "\\\\n").utf8Bytes

  private def serialiseTags(t: Map[String, String]): UTF8Bytes = {
    t.toList
      .take(maximumTagCount)
      .map { case (k, v) => s"${filterName(k)}:${filterTagValue(v)}" }
      .filter(tag => tag.length <= maxTagLength)
      .reduceOption(_ + "," + _)
      .fold(Array.empty[Byte])(ts => s"|#$ts".utf8Bytes)
  }

  private[extras] def serialiseCounter(m: Metric, value: Long): UTF8Bytes =
    s"${filterName(m.name)}:$value|c".utf8Bytes ++ serialiseTags(m.tags)

  private[extras] def serialiseHistogram(m: Metric, value: Long): UTF8Bytes =
    s"${filterName(m.name)}:$value|h|@1.0".utf8Bytes ++ serialiseTags(m.tags)

  private[extras] def serialiseGauge(m: Metric, value: Long): UTF8Bytes =
    s"${filterName(m.name)}:$value|g".utf8Bytes ++ serialiseTags(m.tags)

  private[extras] def serialiseDistribution(m: Metric, value: Long): UTF8Bytes =
    s"${filterName(m.name)}:$value|d|@1.0".utf8Bytes ++ serialiseTags(m.tags)

  private[extras] def serialiseEvent(e: Event): UTF8Bytes = {
    val body = filterEventText(e.body)
    val title = filterEventText(e.title)
    val lengths = s"{${title.length},${body.length}}".utf8Bytes
    val meta = s"t:${e.alertType.value}|p:${e.priority.value}".utf8Bytes
    "_e".utf8Bytes ++ lengths.colon(title).pipe(body).pipe(meta ++ serialiseTags(e.tags))
  }

  private[extras] def applyConfig(m: Metric, config: Config): Metric =
    Metric((config.metricPrefix.toList :+ m.name).mkString("."), config.globalTags ++ m.tags)

  /**
   * Take care of the gymnastics required to send a string to the `to` destination through a socket in F.
   */
  private def send[F[_]](s: DatagramSocket[F], to: SocketAddress[IpAddress], what: UTF8Bytes): F[Unit] =
    s.write(Datagram(to, array(what)))

  /**
   * Create an instance of Metrics that uses a UDP socket to communicate with Datadog.
   */
  def apply[F[_]: Network](config: Config): Resource[F, Metrics[F] with Events[F]] = {
    Network[F].openDatagramSocket().map { sock =>
      new Metrics[F] with Events[F] {
        def counter(m: Metric)(value: Long): F[Unit] =
          send[F](sock, config.agentHost, serialiseCounter(applyConfig(m, config), value))
        def histogram(m: Metric)(value: Long): F[Unit] =
          send[F](sock, config.agentHost, serialiseHistogram(applyConfig(m, config), value))
        def event(event: Event): F[Unit] =
          send[F](sock, config.agentHost, serialiseEvent(event.withTags(config.globalTags ++ event.tags)))
        def gauge(m: Metric)(value: Long): F[Unit] =
          send[F](sock, config.agentHost, serialiseGauge(applyConfig(m, config), value))
        def distribution(m: Metric)(value: Long): F[Unit] =
          send[F](sock, config.agentHost, serialiseDistribution(applyConfig(m, config), value))
      }
    }
  }
}
