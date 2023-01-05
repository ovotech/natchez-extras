package com.ovoenergy.natchez.extras.doobie

import cats.effect.{IO, Ref, Resource, SyncIO}
import cats.syntax.flatMap._
import com.ovoenergy.natchez.extras.doobie.TracedTransactor.Traced
import doobie.h2.H2Transactor.newH2Transactor
import doobie.implicits._
import doobie.util.transactor.Transactor
import munit.CatsEffectSuite
import natchez.{Kernel, Span, TraceValue}

import java.net.URI
import scala.concurrent.ExecutionContext.global

class TracedTransactorTest extends CatsEffectSuite {

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

        override def log(fields: (String, TraceValue)*): IO[Unit] = IO.unit

        override def log(event: String): IO[Unit] = IO.unit

        override def attachError(err: Throwable): IO[Unit] = IO.unit

        override def span(name: String, kernel: Kernel): Resource[IO, Span[IO]] = span(name)
      }
      a.run(spanMock).attempt.flatMap(_ => sps.get)
    }

  val database: SyncIO[FunFixture[Transactor[Traced[IO, *]]]] = ResourceFixture(
    newH2Transactor[IO]("jdbc:h2:mem:test", "foo", "bar", global).map(TracedTransactor("test", _))
  )

  database.test("Trace queries") { db =>
    assertIO(
      run(sql"SELECT 1 WHERE true = ${true: Boolean}".query[Int].unique.transact(db)).map(_.last),
      SpanData("test-db:db.execute:SELECT 1 WHERE true = ?", Map("span.type" -> "db"))
    )
  }

  database.test("Trace updates") { db =>
    case class Test(name: String, age: Int)
    val create = sql"CREATE TABLE a (id INT, name VARCHAR)".update.run
    val insert = sql"INSERT INTO a VALUES (${2: Int}, ${"abc": String})".update.run
    assertIO(
      run((create >> insert).transact(db)).map(_.last),
      SpanData("test-db:db.execute:INSERT INTO a VALUES (?, ?)", Map("span.type" -> "db"))
    )
  }
}
