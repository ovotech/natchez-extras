package com.ovoenergy.effect

import cats.FlatMap
import cats.data.Ior
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import Logging.Tags
import org.slf4j.{LoggerFactory, MDC}

import scala.language.higherKinds

/**
 * A type class representing the ability to log
 * within some effect type F, with MDC information
 * passed through using a StateT or similar
 */
trait Logging[F[_]] {
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

  sealed trait Log
  case class Debug(of: String) extends Log
  case class Info(of: String) extends Log
  case class Error(of: Ior[String, Throwable]) extends Log

  object Error {
    def apply(s: String): Error = Error(Ior.Left(s))
    def apply(t: Throwable): Error = Error(Ior.Right(t))
    def apply(s: String, t: Throwable): Error = Error(Ior.Both(s, t))
  }


  // syntax to summon an instance
  def apply[F[_]: Logging]: Logging[F] = implicitly

  /**
    * An instance of logging using SLF4J
    * requires your F[_] to be a sync
    */
  def slf4jLogging[F[_] : Sync]: F[Logging[F]] =
    Sync[F].delay(LoggerFactory.getLogger("logging")).map { logger =>
      (log, tags) =>
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

  /**
   * Syntax for attaching logging to an effect -
   * the logging will run _before_ the effect is evaluated,
   * so even if say your IO fails you'll get logs.
   */
  implicit class LogSyntax[F[_]: Logging: FlatMap, A](i: F[A]) {
    def log(l: Log): F[A] = Logging[F].log(l, Map.empty) >> i
  }

  /**
   * More syntax, but a standalone function this time
   * to cut down on the Logging[F].log(Info(...)) boilerplate
   */
  def log[F[_] : Logging](what: Log, tags: Tags = Map.empty): F[Unit] =
    Logging[F].log(what, tags)
}
