package com.ovoenergy.natchez.extras.testkit

import cats.effect.concurrent.Ref
import cats.effect.{Clock, ExitCase, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint.TestSpan
import natchez.{EntryPoint, Kernel, Span, TraceValue}

import java.net.URI
import java.time.Instant
import java.util.concurrent.TimeUnit

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
    kernel: Kernel,
    name: String
  )

  def apply[F[_]: Clock](implicit F: Sync[F]): F[TestEntryPoint[F]] =
    Ref.of[F, List[TestSpan]](List.empty).map { submitted =>
      def span(myName: String, k: Kernel): Span[F] = new Span[F] {
        def span(name: String): Resource[F, Span[F]] = makeSpan(name, Some(myName), k)
        def put(fields: (String, TraceValue)*): F[Unit] = F.unit
        def traceId: F[Option[String]] = F.pure(None)
        def spanId: F[Option[String]] = F.pure(None)
        def traceUri: F[Option[URI]] = F.pure(None)
        def kernel: F[Kernel] = F.pure(k)
      }

      def makeSpan(name: String, parent: Option[String], kernel: Kernel): Resource[F, Span[F]] =
        Resource.makeCase(F.delay(span(name, kernel))) { (_, ec) =>
          Clock[F]
            .realTime(TimeUnit.MILLISECONDS)
            .map(Instant.ofEpochMilli)
            .flatMap { time =>
              val span = TestSpan(ec, parent, time, kernel, name)
              submitted.update(_ :+ span)
            }
        }

      new TestEntryPoint[F] {
        def spans: F[List[TestSpan]] = submitted.get
        def root(name: String): Resource[F, Span[F]] = makeSpan(name, None, Kernel(Map.empty))
        def continue(name: String, k: Kernel): Resource[F, Span[F]] = makeSpan(name, None, k)
        def continueOrElseRoot(name: String, k: Kernel): Resource[F, Span[F]] = makeSpan(name, None, k)
      }
    }
}
