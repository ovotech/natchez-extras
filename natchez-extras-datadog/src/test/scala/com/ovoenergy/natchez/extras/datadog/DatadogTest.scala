package com.ovoenergy.natchez.extras.datadog

import cats.effect._
import cats.instances.list._
import cats.syntax.traverse._
import com.ovoenergy.natchez.extras.datadog.Datadog.entryPoint
import com.ovoenergy.natchez.extras.datadog.DatadogTags.SpanType.{Cache, Db, Web}
import com.ovoenergy.natchez.extras.datadog.DatadogTags.spanType
import munit.CatsEffectSuite
import natchez.{EntryPoint, TraceValue}
import org.http4s.Request
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.syntax.literals._
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration._
import natchez.Kernel

/**
 * This tests both the datadog span code itself and the submission of metrics over HTTP Could be expanded but
 * even just these tests exposed concurrency issues with my original code.
 */
class DatadogTest extends CatsEffectSuite {

  def run(f: EntryPoint[IO] => IO[Unit], meta: Map[String, TraceValue] = Map.empty): IO[List[Request[IO]]] =
    TestClient[IO].flatMap(c => entryPoint(c.client, "test", "blah", meta = meta).use(f) >> c.requests)

  test("Obtain the agent host from the parameter") {
    assertIO(
      returns = List(uri"http://example.com/v0.3/traces"),
      obtained = for {
        client <- TestClient[IO]
        ep = entryPoint(client.client, "a", "b", agentHost = uri"http://example.com")
        _        <- ep.use(_.root("foo").use(_ => IO.unit))
        requests <- client.requests
      } yield requests.map(_.uri)
    )
  }

  test("Allow you to modify trace tokens") {
    assertIO(
      returns = Some("foo"),
      obtained = for {
        client <- TestClient[IO]
        ep = entryPoint(client.client, "a", "b", agentHost = uri"http://example.com")
        kernel <- ep.use(_.root("foo").use(s => s.put("traceToken" -> "foo") >> s.kernel))
      } yield kernel.toHeaders.get(ci"X-Trace-Token")
    )
  }

  test("Continue to send HTTP calls even if one of them fails") {

    val test: EntryPoint[IO] => IO[Unit] =
      ep =>
        ep.root("first").use(_ => IO.unit) >>
          IO.sleep(1.second) >>
          ep.root("second").use(_ => IO.unit)

    assertIO(
      returns = 2,
      obtained = for {
        client <- TestClient[IO]
        _      <- client.respondWith(IO.raiseError(new Exception))
        ep = entryPoint(client.client, "a", "b", agentHost = uri"http://example.com")
        _        <- ep.use(test)
        requests <- client.requests
      } yield requests.length
    )
  }

  test("Submit the right info to Datadog when closed") {
    for {
      res   <- run(_.root("bar:res").use(_.put("k" -> "v") >> IO.sleep(1.milli)))
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]])
    } yield {
      val span = spans.flatten.head
      assertEquals(span.name, "bar")
      assertEquals(span.service, "test")
      assertEquals(span.resource, "res")
      assertEquals(span.`type`, None)
      assertEquals(span.duration > 0, true)
      assertEquals(span.meta.get("k"), Some("v"))
      assertEquals(span.meta.contains("traceToken"), true)
    }
  }

  test("Only include the sampling priority metric on the root span") {
    for {
      res   <- run(_.root("root").use(_.span("span").use(_ => IO.sleep(1.milli))))
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
    } yield {
      assertEquals(spans.find(_.parentId.isEmpty).map(_.metrics), Some(Map("_sampling_priority_v1" -> 2.0)))
      assertEquals(spans.find(_.parentId.isDefined).map(_.metrics), Some(Map.empty[String, Double]))
    }
  }

  test("Infer the right span.type from any tags set") {
    List(Web, Cache, Db).traverse { typ =>
      assertIO(
        returns = Some(typ),
        obtained = for {
          res  <- run(_.root("bar:res").use(_.put(spanType(typ))))
          span <- res.flatTraverse(_.as[List[List[SubmittableSpan]]])
        } yield span.flatten.head.`type`
      )
    }
  }

  test("Submit multiple spans across multiple calls when span() is called") {
    for {
      res   <- run(_.root("bar").use(_.span("subspan").use(_ => IO.sleep(1.second))))
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
    } yield {
      assertEquals(spans.map(_.traceId).distinct.length, 1)
      assertEquals(spans.map(_.spanId).distinct.length, 2)
    }
  }

  test("Allow you to override the service name and resource with colons") {
    for {
      res   <- run(_.root("svc:name:res").use(_ => IO.unit))
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
    } yield {
      assertEquals(spans.head.resource, "res")
      assertEquals(spans.head.service, "svc")
      assertEquals(spans.head.name, "name")
    }
  }

  test("Allow you to provide default tags") {
    for {
      res <- run(
        _.root("bar").use(_.span("subspan").use(_ => IO.unit)),
        Map("defaultTag1" -> "some-value", "defaultTag2" -> "some-other-value")
      )
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
    } yield {
      assertEquals(spans.head.meta.get("defaultTag1"), Some("some-value"))
      assertEquals(spans.head.meta.get("defaultTag2"), Some("some-other-value"))
      assertEquals(spans.tail.head.meta.get("defaultTag1"), Some("some-value"))
      assertEquals(spans.tail.head.meta.get("defaultTag2"), Some("some-other-value"))
    }
  }

  test("Allow you to provide default tags using continue") {
    for {
      res <- run(
        _.continue("bar", Kernel(Map.empty)).use(_.span("subspan").use(_ => IO.unit)),
        Map("defaultTag1" -> "some-value", "defaultTag2" -> "some-other-value")
      )
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
    } yield {
      assertEquals(spans.head.meta.get("defaultTag1"), Some("some-value"))
      assertEquals(spans.head.meta.get("defaultTag2"), Some("some-other-value"))
      assertEquals(spans.tail.head.meta.get("defaultTag1"), Some("some-value"))
      assertEquals(spans.tail.head.meta.get("defaultTag2"), Some("some-other-value"))
    }
  }

  test("Allow you to provide default tags using continueOrElseRoot") {
    for {
      res <- run(
        _.continueOrElseRoot("bar", Kernel(Map.empty)).use(_.span("subspan").use(_ => IO.unit)),
        Map("defaultTag1" -> "some-value", "defaultTag2" -> "some-other-value")
      )
      spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
    } yield {
      assertEquals(spans.head.meta.get("defaultTag1"), Some("some-value"))
      assertEquals(spans.head.meta.get("defaultTag2"), Some("some-other-value"))
      assertEquals(spans.tail.head.meta.get("defaultTag1"), Some("some-value"))
      assertEquals(spans.tail.head.meta.get("defaultTag2"), Some("some-other-value"))
    }
  }

  test("Inherit metadata into subspans but only at the time of creation") {
    run(
      _.root("bar:res").use { root =>
        root.put("foo" -> "bar") >> root.span("sub").use(_.put("baz" -> "qux"))
      }
    ).flatMap(_.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)).map { spans =>
      val rootSpan = spans.find(_.name == "bar").get
      val subSpan = spans.find(_.name == "sub").get
      assertEquals(
        subSpan.meta.view.filterKeys(_ != "traceToken").toMap,
        Map("foo" -> "bar", "baz" -> "qux")
      )
      assertEquals(
        rootSpan.meta.view.filterKeys(_ != "traceToken").toMap,
        Map("foo" -> "bar")
      )
    }
  }

  test("Sets the error flag when the span's meta contains an error") {
    List("error.message", "error.msg").traverse { errorMessageKey =>
      val spans =
        for {
          res <- run(
            _.root("service:resource").use { root =>
              root.put(errorMessageKey -> "Some error")
            }
          )
          spans <- res.flatTraverse(_.as[List[List[SubmittableSpan]]]).map(_.flatten)
        } yield spans

      assertIO(
        obtained = spans.map(_.head.error),
        returns = Some(1)
      )
    }
  }
}
