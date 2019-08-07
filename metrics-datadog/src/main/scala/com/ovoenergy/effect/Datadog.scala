package com.ovoenergy.effect

import java.net.InetSocketAddress

import cats.effect._
import cats.instances.option._
import com.ovoenergy.effect.Metrics.Metric
import fs2.Chunk.array
import fs2.io.udp._

object Datadog {

  case class Config(
    agentHost: InetSocketAddress,
    metricPrefix: Option[String],
    globalTags: Map[String, String]
  )

  private implicit val group: AsynchronousSocketGroup =
    AsynchronousSocketGroup.apply

  /**
   * Datadog enforces all metrics must start with a letter
   * and not contain any chars other than letters, numbers and underscores
   */
  private[effect] def filterChars(s: String): String =
    s.dropWhile(!_.isLetter).replaceAll("[^A-Za-z0-9]+", "_" )

  private def serialiseTags(t: Map[String, String]): String = {
    val tagString = t.toList.map { case (k, v) => s"${filterChars(k)}:${filterChars(v)}"}.mkString(",")
    if (tagString.nonEmpty) s"|#$tagString" else ""
  }

  private[effect] def serialiseCounter(m: Metric, value: Long): String =
    s"${filterChars(m.name)}:$value|c${serialiseTags(m.tags)}"

  private[effect] def serialiseHistogram(m: Metric, value: Long): String =
    s"${filterChars(m.name)}:$value|h|@1.0${serialiseTags(m.tags)}"

  private[effect] def applyConfig(m: Metric, config: Config): Metric =
    Metric((config.metricPrefix.toList :+ m.name).mkString("."), config.globalTags ++ m.tags)

  /**
   * Take care of the gymnastics required to send a string to the `to` destination through
   * a socket in F before turning the resulting unit into a `G[Unit]` so our types line up
   */
  private def send[F[_]: Effect, G[_]: Async](skt: Socket[F], to: InetSocketAddress, what: String): G[Unit] =
    Async[G].liftIO(Effect[F].toIO(skt.write(Packet(to, array(what.getBytes)))))

  /**
   * Create an instance of Metrics that uses a UDP socket to communicate with Datadog.
   */
  def apply[F[_]: ContextShift, G[_]](config: Config)(
    implicit
    G: Async[G],
    F: ConcurrentEffect[F]
  ): Resource[F, Metrics[G]] =
    Socket[F]().map { sock =>
      new Metrics[G] {
        def counter(m: Metric): G[Long => G[Unit]] =
          G.pure(v => send[F, G](sock, config.agentHost, serialiseCounter(applyConfig(m, config), v)))
        def histogram(m: Metric): G[Long => G[Unit]] =
          G.pure(v => send[F, G](sock, config.agentHost, serialiseHistogram(applyConfig(m, config), v)))
      }
    }
}
