package com.ovoenergy.natchez.extras.doobie

import cats.data.Kleisli
import cats.effect.{Async, Resource}
import cats.implicits.catsSyntaxFlatMapOps
import com.ovoenergy.natchez.extras.core.Config
import com.ovoenergy.natchez.extras.core.Config.ServiceAndResource
import doobie.util.log.LogHandler
import doobie.{KleisliInterpreter, WeakAsync}
import doobie.util.transactor.Transactor
import natchez.{Span, Trace}

import java.sql.{Connection, PreparedStatement, ResultSet}

object TracedTransactor {
  private val DefaultResourceName = "db.execute"

  type Traced[F[_], A] = Kleisli[F, Span[F], A]
  def apply[F[_]: Async](
    service: String,
    transactor: Transactor[F],
    logHandler: LogHandler[Traced[F, *]]
  ): Transactor[Traced[F, *]] = {
    val kleisliTransactor = transactor
      .mapK(Kleisli.liftK[F, Span[F]])(implicitly, Async.asyncForKleisli(implicitly))
    trace(ServiceAndResource(s"$service-db", DefaultResourceName), kleisliTransactor, logHandler)
  }

  def apply[F[_]: Async](
    service: String,
    transactor: Transactor[F]
  ): Transactor[Traced[F, *]] = {
    val kleisliTransactor = transactor
      .mapK(Kleisli.liftK[F, Span[F]])(implicitly, Async.asyncForKleisli(implicitly))
    trace(
      ServiceAndResource(s"$service-db", DefaultResourceName),
      kleisliTransactor,
      LogHandler.noop[Traced[F, *]]
    )
  }

  private val commentNamedQueryRegEx = """--\s*Name:\s*(\w+)""".r

  private def extractQueryNameOrSql(sql: String): String =
    commentNamedQueryRegEx.findFirstMatchIn(sql).flatMap(m => Option(m.group(1))).getOrElse(sql)

  private def formatQuery(q: String): String =
    q.replace("\n", " ").replaceAll("\\s+", " ").trim()

  def trace[F[_]: Trace: Async](
    config: Config,
    transactor: Transactor[F],
    logHandler: LogHandler[F]
  ): Transactor[F] =
    transactor
      .copy(
        interpret0 = createInterpreter(config, Async[F], logHandler).ConnectionInterpreter,
        connect0 = in =>
          Trace[Resource[F, *]].span(config.fullyQualifiedSpanName("connect"))(
            Trace[Resource[F, *]].put("span.type" -> "db") >> transactor.connect(in)
          )
      )

  private def createInterpreter[F[_]: Trace](
    config: Config,
    F: Async[F],
    logHandler: LogHandler[F]
  ): KleisliInterpreter[F] = {
    new KleisliInterpreter[F](logHandler)(WeakAsync.doobieWeakAsyncForAsync(F)) {

      override lazy val PreparedStatementInterpreter: PreparedStatementInterpreter =
        new PreparedStatementInterpreter {

          type TracedOp[A] = Kleisli[F, PreparedStatement, A] //PreparedStatement => F[A]

          def runTraced[A](f: TracedOp[A]): TracedOp[A] =
            Kleisli {
              case TracedStatement(p, sql) =>
                Trace[F].span(config.fullyQualifiedSpanName(formatQuery(extractQueryNameOrSql(sql))))(
                  Trace[F].put("span.type" -> "db") >> f(p)
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
          override def prepareStatement(a: String): Kleisli[F, Connection, PreparedStatement] =
            super.prepareStatement(a).map(TracedStatement(_, a): PreparedStatement)
          override def getTypeMap: Nothing =
            super.getTypeMap.asInstanceOf // See: https://github.com/tpolecat/doobie/blob/v1.0.0-RC4/modules/core/src/test/scala/doobie/util/StrategySuite.scala#L47
          override def commit: Kleisli[F, Connection, Unit] =
            Kleisli { c =>
              Trace[F].span(config.fullyQualifiedSpanName("commit"))(
                Trace[F].put("span.type" -> "db") >> super.commit(c)
              )
            }
        }
    }
  }
}
