package com.ovoenergy.natchez.extras.doobie

import cats.effect.unsafe.implicits._
import cats.effect.{IO, Ref, Resource}
import cats.syntax.flatMap._
import com.ovoenergy.natchez.extras.doobie.TracedTransactor.Traced
import doobie.h2.H2Transactor.newH2Transactor
import doobie.implicits._
import doobie.util.transactor.Transactor
import natchez.{Kernel, Span, TraceValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URI
import scala.concurrent.ExecutionContext

class TracedTransactorTest extends AnyWordSpec with Matchers {

  case class SpanData(
    name: String,
    tags: Map[String, TraceValue]
  )

  def run[A](a: Traced[IO, A]): IO[List[SpanData]] =
    Ref.of[IO, List[SpanData]](List(SpanData("root", Map.empty))).flatMap { sps =>
      lazy val spanMock: Span[IO] = new Span[IO] {
        def span(name: String): Resource[IO, Span[IO]] =
          Resource.eval(sps.update(_ :+ SpanData(name, Map.empty)).as(spanMock))
        def kernel: IO[Kernel] =
          IO.pure(Kernel(Map.empty))
        def put(fields: (String, TraceValue)*): IO[Unit] =
          sps.update(s => s.dropRight(1) :+ s.last.copy(tags = s.last.tags ++ fields.toMap))
        def traceId: IO[Option[String]] =
          IO.pure(None)
        def spanId: IO[Option[String]] =
          IO.pure(None)
        def traceUri: IO[Option[URI]] =
          IO.pure(None)
      }
      a.run(spanMock).attempt.flatMap(_ => sps.get)
    }

  val db: Resource[IO, Transactor[Traced[IO, *]]] =
    newH2Transactor[IO]("jdbc:h2:mem:test", "foo", "bar", ExecutionContext.global)
      .map(TracedTransactor("test", _))

  "TracedTransactor" should {

    "Trace queries" in {
      val query = sql"""
                       |SELECT 1
                       |WHERE true = ${true: Boolean}
      """.stripMargin.query[Int].unique
      val res = db.use(d => run(query.transact(d))).unsafeRunSync()
      res.last shouldBe SpanData("test-db:db.execute:SELECT 1 WHERE true = ?", Map("span.type" -> "db"))
    }

    "Trace updates" in {
      case class Test(name: String, age: Int)
      val create = sql"CREATE TABLE a (id INT, name VARCHAR)".update.run
      val insert = sql"INSERT INTO a VALUES (${2: Int}, ${"abc": String})".update.run
      val res = db.use(d => run((create >> insert).transact(d))).unsafeRunSync()
      res.last shouldBe SpanData("test-db:db.execute:INSERT INTO a VALUES (?, ?)", Map("span.type" -> "db"))
    }
  }
}
