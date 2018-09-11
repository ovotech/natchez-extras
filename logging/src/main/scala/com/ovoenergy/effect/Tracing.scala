package com.ovoenergy.effect
import java.util.UUID

import cats.data.StateT
import cats.effect.{IO, Sync}
import cats.{FlatMap, Functor, Id, Monad, Monoid, MonoidK, ~>}
import com.ovoenergy.effect.Tracing.TraceContext
import cats.syntax.functor._
import cats.instances.option._
import cats.syntax.flatMap._
import com.ovoenergy.effect.Logging.Tags

trait Tracing[F[_]] {
  def reset: F[Unit]
  def put(traceToken: TraceContext[Id]): F[Unit]
  def get: F[TraceContext[Id]]
}

object Tracing {

  type Traced[F[_], A] = StateT[F, TraceContext[Option], A]
  type TraceIO[A] = Traced[IO, A]

  case class TraceToken(value: String) extends AnyVal
  case class TraceContext[G[_]](token: G[TraceToken], mdc: Map[String, String])

  def apply[F[_] : Tracing]: Tracing[F] = implicitly

  object TraceContext {
    implicit def monoid[G[_] : MonoidK]: Monoid[TraceContext[G]] =
      new Monoid[TraceContext[G]] {
        def empty =
          TraceContext(MonoidK[G].empty, Map.empty)
        def combine(x: TraceContext[G], y: TraceContext[G]) =
          TraceContext(MonoidK[G].combineK(x.token, y.token), x.mdc ++ y.mdc)
      }
  }

  /**
   * Given some F[_], produce a Tracing instance for a Traced[F[_], ?], which
   * boils down to a StateT containing MDC info + a trace token
   */
  implicit def instance[F[_]: Sync]: Tracing[Traced[F, ?]] =
    new Tracing[Traced[F, ?]] {

      def reset: Traced[F, Unit] =
        StateT.modify(_ => TraceContext[Option](None, Map.empty))

      def get: Traced[F, TraceContext[Id]] =
        StateT {
          case TraceContext(None, mdc) =>
            val newToken = Sync[F].delay(TraceToken(UUID.randomUUID.toString))
            newToken.map(t => TraceContext(Option(t), mdc) -> TraceContext[Id](t, mdc))
          case s @ TraceContext(Some(token), mdc) =>
            Monad[F].pure(s -> TraceContext[Id](token, mdc))
        }

      def put(ctx: TraceContext[Id]): Traced[F, Unit] =
        StateT.set(ctx.copy[Option](token = Some(ctx.token)))
    }

  /**
    * Turn a Traced[F, A] into an F[A]
    * by running it with an initial empty trace context
    */
  def dropTracing[F[_]: Monad]: Traced[F, ?] ~> F =
    new (Traced[F, ?] ~> F) { def apply[A](t: Traced[F, A]): F[A] = t.runEmptyA }

  /**
    * The following functions are syntax sugar for various operations on the trace context
    * most notably trace, which takes a function requiring tags and provides them..
    * useful for logging
    */
  def token[F[_] : Tracing : Functor]: F[TraceToken] =
    Tracing[F].get.map(_.token)

  def putToken[F[_] : Tracing : Monad](token: TraceToken): F[Unit] =
    Tracing[F].get.flatMap(c => Tracing[F].put(c.copy[Id](token = token)))

  def mdc[F[_] : Tracing: Functor]: F[Tags] =
    Tracing[F].get.map(c => c.mdc.updated("traceToken", c.token.value))

  def trace[F[_]: FlatMap](fn: Tags => F[Unit])(implicit t: Tracing[Traced[F, ?]]): Traced[F, Unit] =
    mdc[Traced[F, ?]].flatMapF(fn)

  def putMdc[F[_] : Tracing : Monad](tags: (String, String)*): F[Unit] =
    Tracing[F].get.flatMap(c => Tracing[F].put(c.copy(mdc = c.mdc ++ tags.toMap)))
}
