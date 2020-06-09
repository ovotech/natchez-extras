package com.ovoenergy.effect.natchez

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, ContextShift, IO, Resource}
import cats.syntax.functor._
import com.ovoenergy.effect.natchez.TracedTransactor.Traced
import doobie.h2.H2Transactor.newH2Transactor
import doobie.implicits._
import doobie.util.transactor.Transactor
import natchez.{Kernel, Span, TraceValue}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.syntax.flatMap._

import scala.concurrent.ExecutionContext.global

class TracedTransactorTest extends AnyWordSpec with Matchers {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  case class SpanData(
    name: String,
    tags: Map[String, TraceValue]
  )

  def run[A](a: Traced[IO, A]): IO[List[SpanData]] =
    Ref.of[IO, List[SpanData]](List(SpanData("root", Map.empty))).flatMap { sps =>
      lazy val spanMock: Span[IO] = new Span[IO] {
        def span(name: String): Resource[IO, Span[IO]] =
          Resource.liftF(sps.update(_ :+ SpanData(name, Map.empty)).as(spanMock))
        def kernel: IO[Kernel] =
          IO.pure(Kernel(Map.empty))
        def put(fields: (String, TraceValue)*): IO[Unit] =
          sps.update(s => s.dropRight(1) :+ s.last.copy(tags = s.last.tags ++ fields.toMap))
      }
      a.run(spanMock).attempt.flatMap(_ => sps.get)
    }

  val db: Resource[IO, Transactor[Traced[IO, *]]] =
    for {
      block <- Blocker[IO]
      xa    <- newH2Transactor[IO]("jdbc:h2:mem:test", "foo", "bar", global, block)
    } yield TracedTransactor("test", block, xa)

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
