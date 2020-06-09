package com.ovoenergy.effect.natchez

import java.sql.{Connection, PreparedStatement, ResultSet}

import cats.data.Kleisli
import cats.effect.{Async, Blocker, Concurrent, ContextShift}
import doobie.free.KleisliInterpreter
import doobie.util.transactor._
import natchez.{Span, Trace}
import cats.syntax.flatMap._

object TracedTransactor {

  type Traced[F[_], A] = Kleisli[F, Span[F], A]

  private def formatQuery(q: String): String =
    q.replace("\n", " ").replaceAll("\\s+", " ").trim()

  def apply[F[_]: Concurrent: ContextShift](
    service: String,
    blocking: Blocker,
    transactor: Transactor[F]
  ): Transactor[Traced[F, *]] =
    transactor
      .mapK(Kleisli.liftK[F, Span[F]])
      .copy(
        interpret0 = (
          new KleisliInterpreter[Traced[F, *]] {

            val blocker: Blocker = blocking
            val trace: Trace.KleisliTrace[F] = Trace[Traced[F, *]]
            val contextShiftM: ContextShift[Traced[F, *]] = ContextShift.deriveKleisli[F, Span[F]]
            implicit val asyncM: Async[Traced[F, *]] = Async.catsKleisliAsync[F, Span[F]]

            override lazy val PreparedStatementInterpreter: PreparedStatementInterpreter =
              new PreparedStatementInterpreter {

                type TracedOp[A] = Kleisli[Traced[F, *], PreparedStatement, A]

                def runTraced[A](f: TracedOp[A]): TracedOp[A] =
                  Kleisli {
                    case TracedStatement(p, sql) =>
                      trace.span(s"$service-db:db.execute:${formatQuery(sql)}")(
                        trace.put("span.type" -> "db") >>
                        f(p)
                      )
                    case a =>
                      f(a)
                  }

                override val executeBatch: TracedOp[Array[Int]] =
                  runTraced(super.executeBatch)

                override val executeLargeBatch: TracedOp[Array[Long]] =
                  runTraced(super.executeLargeBatch)

                override val execute: TracedOp[Boolean] =
                  runTraced(super.execute)

                override val executeUpdate: TracedOp[Int] =
                  runTraced(super.executeUpdate)

                override val executeQuery: TracedOp[ResultSet] =
                  runTraced(super.executeQuery)
              }

            override lazy val ConnectionInterpreter: ConnectionInterpreter =
              new ConnectionInterpreter {
                override def prepareStatement(
                  a: String
                ): Kleisli[Traced[F, *], Connection, PreparedStatement] =
                  super.prepareStatement(a).map(TracedStatement(_, a): PreparedStatement)
              }
          }
        ).ConnectionInterpreter
      )
}
