package com.ovoenergy.effect

import java.net.InetSocketAddress

import cats.effect._
import com.ovoenergy.effect.Events.Event
import com.ovoenergy.effect.Metrics.Metric
import fs2.Chunk.array
import fs2.io.udp._

object Datadog {

  // just for readability
  type UTF8Bytes = Array[Byte]

  private implicit class StringOps(s: String) {
    def utf8Bytes: UTF8Bytes = s.getBytes("UTF-8")
  }

  private implicit class ByteArrayOps(bs: UTF8Bytes) {
    def sep(char: Char)(other: UTF8Bytes): UTF8Bytes = (bs :+ char.toByte) ++ other
    def colon: UTF8Bytes => UTF8Bytes = sep(':')
    def pipe: UTF8Bytes => UTF8Bytes = sep('|')
  }

  case class Config(
    agentHost: InetSocketAddress,
    metricPrefix: Option[String],
    globalTags: Map[String, String]
  )

  /**
   * Apparently the maximum UDP packet size is 65535 bytes (at the absolute maximum)
   * and we have many different strings in a packet so we only allow each one to be 1Kb
   */
  val maxStringLength = 1000

  /**
   * A basic sanity check for the maximum number of tags to send to Datadog
   * 100 x 2000 (key + value size) = 20Kb so well within a reasonable budget
   */
  val maximumTagCount = 100

  /**
   * Datadog enforces all metrics must start with a letter
   * and not contain any chars other than letters, numbers and underscores
   */
  private[effect] def filterName(s: String): UTF8Bytes =
    s.dropWhile(!_.isLetter).replaceAll("[^A-Za-z0-9.]+", "_").take(maxStringLength).utf8Bytes

  /**
   * More lenient filtering for tag values,
   * we allow alpha numeric characters, slashes, hyphens and numbers
   */
  private[effect] def filterValue(s: String): UTF8Bytes =
    s.replaceAll("[^A-Za-z0-9./\\-]+", "_").take(maxStringLength).utf8Bytes

  /**
   * Datadog receives events as binary byte arrays then converts them into UTF-8 strings
   * https://github.com/DataDog/datadog-agent/blob/21a80ab80de389adb9c74e3e9a7162f83fda3e0c/pkg/dogstatsd/parse_events.go#L62
   * As such we obtain the byte values from the string in UTF-8
   * We take only the first 2k chars just for a basic sanity check
   */
  private[effect] def filterEventText(s: String): UTF8Bytes =
    s.take(maxStringLength).toCharArray.flatMap {
      case c if c == '\n' || c == '\r' => "\\n".utf8Bytes
      case c => s"$c".utf8Bytes
    }

  private def serialiseTags(t: Map[String, String]): UTF8Bytes = {
    t.toList
      .take(maximumTagCount)
      .map { case (k, v) => filterName(k).colon(filterValue(v)) }
      .reduceOption[UTF8Bytes] { case (a, b) => a.sep(',')(b) }
      .fold(Array.empty[Byte])(bs => "|#".utf8Bytes ++ bs)
  }

  private[effect] def serialiseCounter(m: Metric, value: Long): UTF8Bytes =
    filterName(m.name).colon(value.toString.utf8Bytes.pipe("c".utf8Bytes ++ serialiseTags(m.tags)))

  private[effect] def serialiseHistogram(m: Metric, value: Long): UTF8Bytes =
    filterName(m.name).colon(value.toString.utf8Bytes.pipe("h|@1.0".utf8Bytes ++ serialiseTags(m.tags)))

  private[effect] def serialiseEvent(e: Event): UTF8Bytes = {
    val body = filterEventText(e.body)
    val title = filterEventText(e.title)
    val lengths = s"{${title.length},${body.length}}".utf8Bytes
    val meta = s"t:${e.alertType.value}|p:${e.priority.value}".utf8Bytes
    "_e".utf8Bytes ++ lengths.colon(title).pipe(body).pipe(meta ++ serialiseTags(e.tags))
  }

  private[effect] def applyConfig(m: Metric, config: Config): Metric =
    Metric((config.metricPrefix.toList :+ m.name).mkString("."), config.globalTags ++ m.tags)

  /**
   * Take care of the gymnastics required to send a string to the `to` destination through
   * a socket in F before turning the resulting unit into a `G[Unit]` so our types line up
   */
  private def send[F[_]: Effect, G[_]: Async](skt: Socket[F], to: InetSocketAddress, what: UTF8Bytes): G[Unit] =
    Async[G].liftIO(Effect[F].toIO(skt.write(Packet(to, array(what)))))

  /**
   * Create an instance of Metrics that uses a UDP socket to communicate with Datadog.
   */
  def apply[F[_]: ContextShift, G[_]](config: Config)(
    implicit
    G: Async[G],
    F: ConcurrentEffect[F]
  ): Resource[F, Metrics[G] with Events[G]] =
    Blocker[F].flatMap(SocketGroup[F]).flatMap(_.open()).map { sock =>
      new Metrics[G] with Events[G] {
        def counter(m: Metric)(value: Long): G[Unit] =
          send[F, G](sock, config.agentHost, serialiseCounter(applyConfig(m, config), value))
        def histogram(m: Metric)(value: Long): G[Unit] =
          send[F, G](sock, config.agentHost, serialiseHistogram(applyConfig(m, config), value))
        def event(event: Event): G[Unit] =
          send[F, G](sock, config.agentHost, serialiseEvent(event.withTags(config.globalTags ++ event.tags)))
      }
    }
}
