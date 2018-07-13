package com.ovoenergy.effect

import cats.FlatMap
import cats.effect.Sync
import com.typesafe.scalalogging.LazyLogging
import cats.syntax.flatMap._
import org.slf4j.MDC

import scala.language.higherKinds

/**
  * A type class representing the ability to log
  * within some effect type F
  */
trait Logging[F[_]] {
  def log(message: Logging.Log): F[Unit]
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
    def tags: Tags
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
    def log(message: Log): F[Unit] = Sync[F].delay {
      message.tags.foreach {
        case (k, v) =>
          MDC.put(k, v)
      }
      message match {
        case Debug(content, _) => logger.debug(content)
        case Info(content, _)  => logger.info(content)
        case Error(content, _) => logger.error(content)
      }
      message.tags.keys.foreach(MDC.remove)
    }
  }

  /**
    * Syntax for attaching logging to an effect -
    * the logging will run _before_ the effect is evaluated, so even if say your IO fails
    * you'll get logs
    */
  implicit class LogSyntax[F[_]: Logging: FlatMap, A](i: F[A]) {
    def log(l: Log): F[A] = Logging[F].log(l) >> i
  }
}
