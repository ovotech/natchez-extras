package com.ovoenergy.natchez.extras.slf4j

import cats.Monad
import cats.data.OptionT
import cats.effect.kernel.Resource.ExitCase
import cats.effect.{Ref, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import natchez.TraceValue.StringValue
import natchez.{Kernel, Span, TraceValue}
import org.slf4j.{Logger, LoggerFactory, MDC}
import java.net.URI
import java.util.UUID.randomUUID

case class Slf4jSpan[F[_]: Sync](
  mdc: Ref[F, Map[String, TraceValue]],
  logger: Logger,
  token: String,
  name: String
) extends Span[F] {

  def put(fields: (String, TraceValue)*): F[Unit] =
    mdc.update(m => fields.foldLeft(m) { case (m, (k, v)) => m.updated(k, v) })

  def kernel: F[Kernel] =
    Monad[F].pure(Kernel(Map("X-Trace-Token" -> token)))

  def span(name: String): Resource[F, Span[F]] =
    Resource.eval(mdc.get).flatMap(Slf4jSpan.create(name, Some(token), _)).widen

  def traceId: F[Option[String]] =
    Sync[F].pure(Some(token))

  def spanId: F[Option[String]] =
    Sync[F].pure(None)

  def traceUri: F[Option[URI]] =
    Sync[F].pure(None)

  override def log(fields: (String, TraceValue)*): F[Unit] =
    mdc.update(_ ++ fields)

  override def log(event: String): F[Unit] = log("event" -> TraceValue.StringValue(event))

  override def attachError(err: Throwable): F[Unit] = {
    mdc.update(
      _ ++ Map(
        "exit.case" -> "error",
        "exit.error.class" -> err.getClass.getName,
        "exit.error.message" -> err.getMessage,
        "exit.error.stackTrace" -> err.getStackTrace.map(_.toString).mkString("\\n ")
      )
    )
  }

  override def span(name: String, kernel: Kernel): Resource[F, Span[F]] =
    Resource.eval(Slf4jSpan.fromKernel(name, kernel)).widen.flatten
}

object Slf4jSpan {

  private def log[F[_]: Sync](logger: Logger, mdc: Map[String, TraceValue])(f: Logger => Unit): F[Unit] =
    Sync[F].delay {
      mdc.foreach { case (k, v) => MDC.put(k, v.value.toString) }
      f(logger)
      MDC.clear()
    }

  def fromKernel[F[_]: Sync](name: String, k: Kernel): F[Resource[F, Slf4jSpan[F]]] =
    Sync[F]
      .fromEither(
        k.toHeaders
          .find(_._1.toLowerCase == "x-trace-token")
          .map(_._2)
          .toRight(new Exception("Missing X-Trace-Token header"))
      )
      .map { token =>
        create(name, Some(token), Map.empty)
      }

  def create[F[_]: Sync](
    name: String,
    token: Option[String] = None,
    mdc: Map[String, TraceValue] = Map.empty
  ): Resource[F, Slf4jSpan[F]] =
    Resource.makeCase(
      for {
        logger <- Sync[F].delay(LoggerFactory.getLogger("natchez"))
        token  <- OptionT.fromOption[F](token).getOrElseF(Sync[F].delay(randomUUID.toString))
        _      <- log(logger, mdc.updated("traceToken", StringValue(token)))(_.info(s"$name started"))
        mdc    <- Ref.of[F, Map[String, TraceValue]](mdc)
      } yield Slf4jSpan(mdc, logger, token, name)
    )(complete)

  def complete[F[_]: Sync](span: Slf4jSpan[F], exitCase: ExitCase): F[Unit] = {
    span.mdc.get.map(_.updated("traceToken", StringValue(span.token))).flatMap { mdc =>
      exitCase match {
        case ExitCase.Succeeded  => log(span.logger, mdc)(_.info(s"${span.name} success"))
        case ExitCase.Errored(e) => log(span.logger, mdc)(_.error(s"${span.name} error", e))
        case ExitCase.Canceled   => log(span.logger, mdc)(_.info(s"${span.name} cancelled"))
      }
    }
  }
}
