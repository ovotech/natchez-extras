package com.ovoenergy.natchez.extras.doobie

import cats.data.Kleisli
import cats.effect.Async
import doobie.WeakAsync
import doobie.free.KleisliInterpreter
import doobie.util.transactor.Transactor
import natchez.{Span, Trace}

import java.sql.{Connection, PreparedStatement, ResultSet}

object TracedTransactor {

  type Traced[F[_], A] = Kleisli[F, Span[F], A]

  private def formatQuery(q: String): String =
    q.replace("\n", " ").replaceAll("\\s+", " ").trim()

  def apply[F[_]: Async](
    service: String,
    transactor: Transactor[F]
  ): Transactor[Traced[F, *]] =
    transactor
      .mapK(Kleisli.liftK[F, Span[F]])(implicitly, Async.asyncForKleisli(implicitly))
      .copy(
        interpret0 = new KleisliInterpreter[Traced[F, *]] {

          implicit val asyncM: WeakAsync[Traced[F, *]] =
            WeakAsync.doobieWeakAsyncForAsync(Async.asyncForKleisli[F, Span[F]])

          val trace: Trace[Traced[F, *]] =
            Trace.kleisliInstance

          override lazy val PreparedStatementInterpreter: PreparedStatementInterpreter =
              new PreparedStatementInterpreter {

                type TracedOp[A] = Kleisli[Traced[F, *], PreparedStatement, A]

                def runTraced[A](f: TracedOp[A]): TracedOp[A] =
                  Kleisli {
                    case TracedStatement(p, sql) =>
                      trace.span(s"$service-db:db.execute:${formatQuery(sql)}")(
                        trace.put("span.type" -> "db").flatMap(_ => f(p))
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
                override def prepareStatement(a: String): Kleisli[Traced[F, *], Connection, PreparedStatement] =
                  super.prepareStatement(a).map(TracedStatement(_, a): PreparedStatement)
              }
          }.ConnectionInterpreter
      )
}
