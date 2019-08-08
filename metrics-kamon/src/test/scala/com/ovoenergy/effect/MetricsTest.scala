package com.ovoenergy.effect

import cats.Id
import cats.data.WriterT
import cats.instances.list._
import cats.instances.map._
import Metrics.Metric
import org.scalatest.{Matchers, WordSpec}

class MetricsTest extends WordSpec with Matchers {

  "Metrics metric function" should {

    type LogWriter[A] = WriterT[Id, Map[Metric, List[Long]], A]

    implicit val testMetrics: Metrics[LogWriter] = new Metrics[LogWriter] {
      override def counter(metric: Metric): LogWriter[Long => LogWriter[Unit]] = {
        val writer: Long => LogWriter[Unit] = value => WriterT.tell(Map(metric -> List(value)))

        WriterT.liftF[Id, Map[Metric, List[Long]], Long => LogWriter[Unit]](writer)
      }

      override def histogram(metric: Metric): LogWriter[Long => LogWriter[Unit]] = {
        val writer: Long => LogWriter[Unit] = value => WriterT.tell(Map(metric -> List(value)))

        WriterT.liftF[Id, Map[Metric, List[Long]], Long => LogWriter[Unit]](writer)
      }
    }

    "Pass through the right counter metrics to the type class" in {
      val metric = Metric("foo", tags = Map("bar" -> "baz"))
      val putValue = Metrics[LogWriter].counter(metric)

      val result = for {
        putValue <- putValue
        _        <- putValue(1)
        _        <- putValue(2)
        res      <- putValue(3)
      } yield res

      result.run._1 shouldEqual Map(
        metric -> List(1, 2, 3)
      )
    }

    "Pass through the right histogram metrics to the type class" in {
      val metric = Metric("his", tags = Map("tog" -> "ram"))
      val histogram = Metrics[LogWriter].histogram(metric)

      val result = for {
        recordMe <- histogram
        _        <- recordMe(1)
        res      <- recordMe(2)
      } yield res

      result.run._1 shouldEqual Map(
        metric -> List(1, 2)
      )
    }
  }
}
