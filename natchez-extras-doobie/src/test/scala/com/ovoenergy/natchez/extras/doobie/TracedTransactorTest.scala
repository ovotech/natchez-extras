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
import natchez.Tags

class TracedTransactorTest extends CatsEffectSuite {

  case class SpanData(
    name: String,
    tags: Map[String, TraceValue]
  )

  def run[A](a: Traced[IO, A]): IO[List[SpanData]] =
    Ref.of[IO, List[SpanData]](List(SpanData("root", Map.empty))).flatMap { sps =>
      lazy val spanMock: Span[IO] = new Span[IO] {
        def span(name: String, options: Span.Options): Resource[IO, Span[IO]] =
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
        def attachError(err: Throwable, fields: (String, TraceValue)*): IO[Unit] =
          put(Tags.error(true) :: fields.toList: _*)
        def log(event: String): IO[Unit] = put("event" -> TraceValue.StringValue(event))
        def log(fields: (String, TraceValue)*): IO[Unit] = put(fields: _*)
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

  database.test("Trace queries with commented name") { db =>
    assertIO(
      run(sql"""-- Name: selectOne
              SELECT 1 WHERE true = ${true: Boolean}
           """.query[Int].unique.transact(db)).map(_.last),
      SpanData("test-db:db.execute:selectOne", Map("span.type" -> "db"))
    )
  }

  database.test("Trace updates") { db =>
    val create = sql"CREATE TABLE a (id INT, name VARCHAR)".update.run
    val insert =
      sql"INSERT INTO a VALUES (${2: Int}, ${"abc": String})".update.run
    assertIO(
      run((create >> insert).transact(db)).map(_.last),
      SpanData("test-db:db.execute:INSERT INTO a VALUES (?, ?)", Map("span.type" -> "db"))
    )
  }

  database.test("Trace updates withUniqueGeneratedKeys") { db =>
    val create = sql"CREATE TABLE a (id INT, name VARCHAR)".update.run
    val insert =
      sql"INSERT INTO a VALUES (${2: Int}, ${"abc": String})".update.withUniqueGeneratedKeys[String]("name")
    assertIO(
      run((create >> insert).transact(db)).map(_.last),
      SpanData("test-db:db.execute:INSERT INTO a VALUES (?, ?)", Map("span.type" -> "db"))
    )
  }

  database.test("Trace updates with commented name") { db =>
    val create = sql"CREATE TABLE a (id INT, name VARCHAR)".update.run
    val insert =
      sql"""-- Name: createNewA
           INSERT INTO a VALUES (${2: Int}, ${"abc": String})
           """.update.run
    assertIO(
      run((create >> insert).transact(db)).map(_.last),
      SpanData("test-db:db.execute:createNewA", Map("span.type" -> "db"))
    )
  }
}
