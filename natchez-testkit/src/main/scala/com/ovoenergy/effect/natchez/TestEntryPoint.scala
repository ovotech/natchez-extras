package com.ovoenergy.effect.natchez

import java.time.Instant
import java.util.concurrent.TimeUnit

import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.effect.natchez.TestEntryPoint.TestSpan
import natchez.{EntryPoint, Kernel, Span, TraceValue}

/**
 * Test implementation of Natchez that is backed by a Ref
 * and additionally lets you see all the completed spans
 */
trait TestEntryPoint[F[_]] extends EntryPoint[F] {
  def spans: F[List[TestSpan]]
}

object TestEntryPoint {

  case class TestSpan(
    exitCase: ExitCase[Throwable],
    parent: Option[String],
    completed: Instant,
    name: String
  )

  def apply[F[_]: Clock](implicit F: Sync[F]): F[TestEntryPoint[F]] =
    Ref.of[F, List[TestSpan]](List.empty).map { submitted =>
      def span(myName: String): Span[F] = new Span[F] {
        def span(name: String): Resource[F, Span[F]] = makeSpan(name, Some(myName))
        def put(fields: (String, TraceValue)*): F[Unit] = F.unit
        def kernel: F[Kernel] = F.pure(Kernel(Map.empty))
      }

      def makeSpan(name: String, parent: Option[String]): Resource[F, Span[F]] =
        Resource.makeCase(F.delay(span(name))) { (_, ec) =>
          Clock[F]
            .realTime(TimeUnit.MILLISECONDS)
            .map(Instant.ofEpochMilli)
            .flatMap { time =>
              val span = TestSpan(ec, parent, time, name)
              submitted.update(_ :+ span)
            }
        }

      new TestEntryPoint[F] {
        def spans: F[List[TestSpan]] = submitted.get
        def root(name: String): Resource[F, Span[F]] = makeSpan(name, None)
        def continue(name: String, k: Kernel): Resource[F, Span[F]] = makeSpan(name, None)
        def continueOrElseRoot(name: String, k: Kernel): Resource[F, Span[F]] = makeSpan(name, None)
      }
    }
}
