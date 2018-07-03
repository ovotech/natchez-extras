package com.ovoenergy.effect

import cats.data.StateT
import cats.effect.Sync
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.{Applicative, FlatMap, MonadError}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC

import scala.language.higherKinds

/**
  * A type class representing the ability to log
  * within some effect type F
  */
trait Logging[F[_]] {
  def log(what: Logging.Log): F[Unit]
}

/**
  * Our functional code doesn't want to be sprinkled with effectful log statements
  * and it also doesn't want to be tied to the IO monad, so we define a type class for some F
  * which permits logging, returning an unmodified value wrapped in a (presumably modified) F
  * In production code we can use SLF4J and IO, in tests we can use a Writer[List[Log]]
  */
object Logging {

  type Tags = Map[String, String]

  sealed trait Log {
    def tags: Map[String, String]
  }

  case class Debug(of: String, tags: Tags = Map.empty) extends Log
  case class Info(of: String, tags: Tags  = Map.empty) extends Log
  case class Error(of: String, tags: Tags = Map.empty) extends Log

  // syntax to summon an instance
  def apply[F[_]: Logging]: Logging[F] = implicitly

  /**
    * An instance of logging for a Sync[F] which just wraps the call
    * to the underlying logger
    */
  //noinspection ConvertExpressionToSAM
  def syncLogging[F[_]: Sync]: Logging[F] = new Logging[F] with LazyLogging {
    def log(what: Log): F[Unit] = Sync[F].delay {
      what.tags.foreach {
        case (k, v) =>
          MDC.put(k, v)
      }
      what match {
        case Debug(content, _) => logger.debug(content)
        case Info(content, _)  => logger.info(content)
        case Error(content, _) => logger.error(content)
      }
      what.tags.foreach {
        case (k, _) =>
          MDC.remove(k)
      }
    }
  }

  /**
    * Lift logging for an F with logging into logging for a StateT[F, S, A]
    */
  implicit def stateTLogging[F[_]: Logging: Applicative, S]: Logging[StateT[F, S, ?]] =
    (what: Log) => StateT.liftF(Logging[F].log(what))

  /**
    * Syntax for attaching logging to an effect -
    * the logging will run _before_ the effect is evaluated, so even if say your IO fails
    * you'll get logs
    */
  implicit class LogSyntax[F[_]: Logging: FlatMap, A](i: F[A]) {
    def log(l: Log): F[A] = Logging[F].log(l) >> i
  }
}
