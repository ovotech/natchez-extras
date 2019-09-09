package com.ovoenergy.effect.natchez

import cats.effect._
import cats.effect.concurrent.Ref
import cats.instances.list._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.ovoenergy.effect.natchez.Datadog.entryPoint
import com.ovoenergy.effect.natchez.DatadogSpan.CompletedSpan
import natchez.EntryPoint
import natchez.TraceValue.StringValue
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.http4s.{Request, Response}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

/**
  * This tests both the datadog span code itself and the submission of metrics over HTTP
  * Could be expanded but even just these tests exposed concurrency issues with my original code.
  */
class DatadogTest extends WordSpec with Matchers {

  implicit val timer: Timer[IO] =
    IO.timer(global)

  implicit val cs: ContextShift[IO] =
    IO.contextShift(global)

  def run(f: EntryPoint[IO] => IO[Unit]): IO[List[Request[IO]]] =
    Ref.of[IO, List[Request[IO]]](List.empty).flatMap { ref =>
      val client: Client[IO] = Client(r => Resource.liftF(ref.update(_ :+ r).as(Response[IO]())))
      entryPoint(client, "test", "blah").use(f) >> ref.get
    }


  "Datadog span" should {

    "Submit the right info to Datadog when closed" in {

      val res = run(_.root("bar:res").use(_.put("k" -> StringValue("v")))).unsafeRunSync
      val span = res.flatTraverse(_.as[List[List[CompletedSpan]]]).unsafeRunSync.flatten.head

      span.name shouldBe "bar"
      span.service shouldBe "test"
      span.resource shouldBe "res"
      span.duration > 0 shouldBe true
      span.meta.get("k") shouldBe Some("v")
      span.meta.get("traceToken").isDefined shouldBe true
    }

    "Submit multiple spans across multiple calls when span() is called" in {
      val res = run(_.root("bar").use(_.span("subspan").use(_ => timer.sleep(1.second)))).unsafeRunSync
      val spans = res.flatTraverse(_.as[List[List[CompletedSpan]]]).unsafeRunSync.flatten
      spans.map(_.traceId).distinct.length shouldBe 1
      spans.map(_.spanId).distinct.length shouldBe 2
    }

    "Allow you to override the service name and resource with colons" in {
      val res = run(_.root("svc:name:res").use(_ => IO.unit)).unsafeRunSync
      val spans = res.flatTraverse(_.as[List[List[CompletedSpan]]]).unsafeRunSync.flatten
      spans.head.resource shouldBe "res"
      spans.head.service shouldBe "svc"
      spans.head.name shouldBe "name"
    }

    "Inherit metadata into subspans but only at the time of creation" in {
       val res = run(
         _.root("bar:res").use { root =>
            root.put("foo" -> "bar") >> root.span("sub").use(_.put("baz" -> "qux"))
         }
       ).unsafeRunSync

      val spans = res.flatTraverse(_.as[List[List[CompletedSpan]]]).unsafeRunSync.flatten
      val rootSpan = spans.find(_.name == "bar").get
      val subSpan = spans.find(_.name == "sub").get

      subSpan.meta.filterKeys(_ != "traceToken") shouldBe Map("foo" -> "bar", "baz" -> "qux")
      rootSpan.meta.filterKeys(_ != "traceToken") shouldBe Map("foo" -> "bar")
    }
  }
}
