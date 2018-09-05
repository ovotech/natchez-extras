package com.ovoenergy.effect

import cats.data.{Ior, StateT}
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.{Applicative, FlatMap}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC

import scala.language.higherKinds

/**
 * A type class representing the ability to log
 * within some effect type F.
 */
trait Logging[F[_]] {
  def log(message: Logging.Log): F[Unit]
}

/**
 * Our functional code doesn't want to be sprinkled with effectful log statements
 * and it also doesn't want to be tied to the IO monad, so we define a type class for some F
 * which permits logging, returning an unmodified value wrapped in a (presumably modified) F.
 * In production code we can use SLF4J and IO, in tests we can use a `Writer[List[Log]]`.
 */
object Logging {

  type Tags = Map[String, String]

  sealed trait Log {
    def tags: Tags
  }

  case class Debug(of: String, tags: Tags                 = Map.empty) extends Log
  case class Info(of: String, tags: Tags                  = Map.empty) extends Log
  case class Error(of: Ior[String, Throwable], tags: Tags = Map.empty) extends Log

  object Error {
    def apply(s: String): Error = Error(Ior.Left(s))
    def apply(s: String, tags: Tags): Error = Error(Ior.Left(s), tags)
    def apply(t: Throwable): Error = Error(Ior.Right(t))
    def apply(t: Throwable, tags: Tags): Error = Error(Ior.Right(t), tags)
    def apply(s: String, t: Throwable): Error = Error(Ior.Both(s, t))
    def apply(s: String, t: Throwable, tags: Tags): Error = Error(Ior.Both(s, t), tags)
  }

  // syntax to summon an instance
  def apply[F[_]: Logging]: Logging[F] = implicitly

  /**
   * An instance of logging for a Sync[F] which just wraps the call
   * to the underlying logger
   */
  //noinspection ConvertExpressionToSAM
  def syncLogging[F[_]: Sync]: Logging[F] = new Logging[F] with LazyLogging {
    def log(message: Log): F[Unit] = Sync[F].delay {
      message.tags.foreach {
        case (k, v) =>
          MDC.put(k, v)
      }
      message match {
        case Debug(of, _) =>
          logger.debug(of)
        case Info(of, _) =>
          logger.info(of)
        case Error(of, _) =>
          of.fold(
            logger.error(_),
            e => logger.error(e.getMessage, e),
            (s, t) => logger.error(s, t)
          )
      }
      message.tags.keys.foreach(MDC.remove)
    }
  }

  /**
   * Lift logging for an F with logging into logging for a `StateT[F, S, A]`.
   */
  implicit def stateTLogging[F[_]: Logging: Applicative, S]: Logging[StateT[F, S, ?]] =
    (what: Log) => StateT.liftF(Logging[F].log(what))

  /**
   * Syntax for attaching logging to an effect -
   * the logging will run _before_ the effect is evaluated,
   * so even if say your IO fails you'll get logs.
   */
  implicit class LogSyntax[F[_]: Logging: FlatMap, A](i: F[A]) {
    def log(l: Log): F[A] = Logging[F].log(l) >> i
  }
}
