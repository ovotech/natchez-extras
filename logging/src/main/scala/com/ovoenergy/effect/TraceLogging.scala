package com.ovoenergy.effect

import cats.FlatMap
import cats.syntax.flatMap._
import com.ovoenergy.effect.Logging.{Log, Tags}
import com.ovoenergy.effect.Tracing.mdc
import Logging.Log

/**
  * Syntax for automatically pulling tracing information into logs
  * when your effect type supports both logging and tracing
  */
object TraceLogging {

  def mdcLog[F[_] : Logging: Tracing: FlatMap](what: Log, tags: Tags = Map.empty): F[Unit] =
    mdc.flatMap(mdc => Logging[F].log(what, mdc  ++ tags))
}
