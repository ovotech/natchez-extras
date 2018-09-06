package com.ovoenergy.effect

import java.util.UUID

import cats.data.{Ior, StateT}
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.{Applicative, FlatMap, Monad, Monoid}
import com.ovoenergy.effect.Logging.{Tags, TraceToken}
import org.slf4j.{LoggerFactory, MDC}

import scala.language.higherKinds

/**
 * A type class representing the ability to log
 * within some effect type F, with MDC information
 * passed through using a StateT or similar
 */
trait Logging[F[_]] {

  /**
    * Reset the internal state of the logging
    */
  def reset: F[Unit]

  /**
    * Get the current trace token,
    * or generate a new one if there is none
    */
  def token: F[TraceToken]

  /**
    * Set the current trace token
    */
  def putToken(traceToken: TraceToken): F[Unit]

  /**
    * Get the current MDC info
    */
  def mdc: F[Map[String, String]]

  /**
    * Add a key/value to the MDC
    * that will become available in future calls to mdc
    */
  def putMdc(key: String, value: String): F[Unit]

  /**
    * Log something,
    * joining on MDC + trace token information
    */
  def log(message: Logging.Log, tags: Tags = Map.empty): F[Unit]
}

/**
 * Our functional code doesn't want to be sprinkled with effectful log statements
 * and it also doesn't want to be tied to the IO monad, so we define a type class for some F
 * which permits logging, returning an unmodified value wrapped in a (presumably modified) F.
 * In production code we can use SLF4J and IO, in tests we can use a `Writer[List[Log]]`.
 */
object Logging {

  type Tags = Map[String, String]
  type Traced[F[_], A] = StateT[F, TraceContext, A]

  sealed trait Log
  case class Debug(of: String) extends Log
  case class Info(of: String) extends Log
  case class Error(of: Ior[String, Throwable]) extends Log

  object Error {
    def apply(s: String): Error = Error(Ior.Left(s))
    def apply(t: Throwable): Error = Error(Ior.Right(t))
    def apply(s: String, t: Throwable): Error = Error(Ior.Both(s, t))
  }

  case class TraceToken(value: String) extends AnyVal
  case class TraceContext(token: Option[TraceToken], mdc: Map[String, String])

  object TraceContext {
    implicit val monoid: Monoid[TraceContext] = new Monoid[TraceContext] {
      def combine(x: TraceContext, y: TraceContext) = TraceContext(y.token.orElse(x.token), x.mdc ++ y.mdc)
      def empty = TraceContext(None, Map.empty)
    }
  }

  // syntax to summon an instance
  def apply[F[_]: Logging]: Logging[F] = implicitly

  /**
   * Given some F[_], produce a Logging instance for a Traced[F[_], ?], which
   * boils down to a StateT containing MDC info + a trace token
   */
  def tracedInstance[F[_]: Monad](
    newToken: F[TraceToken],
    execLog: (Log, Tags) => F[Unit]
  ): Logging[Traced[F, ?]] =
    new Logging[Traced[F, ?]] {

      def reset: StateT[F, TraceContext, Unit] =
        StateT.modify(_ => TraceContext.monoid.empty)

      def token: StateT[F, TraceContext, TraceToken] =
        StateT { trace =>
          val token: F[TraceToken] = trace.token.fold(newToken)(Applicative[F].pure)
          token.map(t => trace.copy(token = Some(t)) -> t)
        }

      def mdc: Traced[F, Tags] =
        token.inspect(_.mdc)

      def putToken(traceToken: TraceToken): Traced[F, Unit] =
        StateT.modify(_.copy(token = Some(traceToken)))

      def putMdc(key: String, value: String): Traced[F, Unit] =
        StateT.modify(ctx => ctx.copy(mdc = ctx.mdc.updated(key, value)))

      def log(message: Log, tags: Tags): Traced[F, Unit] =
        mdc.flatMapF(mdc => execLog(message, mdc ++ tags))
   }

  /**
    * Create an instance of logging with no tracing that will ignore any tracing calls
    * This is only intended for unit tests where you're simply not interested in the tracing
    */
  def untracedInstance[F[_]](
    newToken: F[TraceToken],
    execLog: Log => F[Unit]
  )(implicit F: Applicative[F]): Logging[F] =
    new Logging[F] {
      def reset: F[Unit] = F.unit
      def token: F[TraceToken] = newToken
      def putToken(traceToken: TraceToken): F[Unit] = F.unit
      def mdc: F[Map[String, String]] = F.pure(Map.empty)
      def putMdc(key: String, value: String): F[Unit] = F.unit
      def log(message: Log, tags: Tags): F[Unit] = execLog(message)
    }

  /**
    * An instance of logging using SLF4J
    * requires your F[_] to be a sync
    */
  def slf4jLogging[F[_] : Sync]: F[Logging[Traced[F, ?]]] =
    Sync[F].delay(LoggerFactory.getLogger("logging")).map { logger =>
      tracedInstance[F](
        newToken = Sync[F].delay(TraceToken(UUID.randomUUID.toString)),
        execLog = { (log, tags) =>
          Sync[F].delay {
            val context = tags.map { case (k, v) => MDC.putCloseable(k, v) }
            try {
              log match {
                case Debug(what)           => logger.debug(what)
                case Info(what)            => logger.info(what)
                case Error(Ior.Left(a))    => logger.error(a)
                case Error(Ior.Right(a))   => logger.error(a.getMessage, a)
                case Error(Ior.Both(a, t)) => logger.error(a, t)
              }
            } finally {
              context.foreach(_.close)
            }
          }
        }
      )
    }

  /**
   * Syntax for attaching logging to an effect -
   * the logging will run _before_ the effect is evaluated,
   * so even if say your IO fails you'll get logs.
   */
  implicit class LogSyntax[F[_]: Logging: FlatMap, A](i: F[A]) {
    def log(l: Log): F[A] = Logging[F].log(l, Map.empty) >> i
  }

  /**
   * More syntax, but standalone functions this time
   * to cut down on the Logging[F].log(Info(...)) boilerplate
   */
  def info[F[_] : Logging](what: String, tags: Tags = Map.empty): F[Unit] =
    Logging[F].log(Info(what), tags)

  def debug[F[_] : Logging](what: String, tags: Tags = Map.empty): F[Unit] =
    Logging[F].log(Debug(what), tags)

  def error[F[_] : Logging](what: Throwable, tags: Tags = Map.empty): F[Unit] =
    Logging[F].log(Error(Ior.Right(what)), tags)
}
