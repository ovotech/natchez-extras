package com.ovoenergy.natchez.extras.testkit

import cats.effect.kernel.Resource.ExitCase
import cats.effect.{Clock, Ref, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint.CompletedSpan
import natchez.{EntryPoint, Kernel, Span, TraceValue}

import java.net.URI
import java.time.Instant

/**
 * Test implementation of Natchez that is backed by a Ref
 * and additionally lets you see all the completed spans
 */
trait TestEntryPoint[F[_]] extends EntryPoint[F] {
  def spans: F[List[CompletedSpan]]
}

object TestEntryPoint {

  case class CompletedSpan(
    tags: List[(String, TraceValue)],
    parent: Option[String],
    completed: Instant,
    exitCase: ExitCase,
    kernel: Kernel,
    name: String
  )

  private trait TestSpan[F[_]] extends Span[F] {
    def tags: F[List[(String, TraceValue)]]
  }

  def apply[F[_]](implicit F: Sync[F]): F[TestEntryPoint[F]] =
    Ref.of[F, List[CompletedSpan]](List.empty).map { submitted =>
      def makeSpan(name: String, parent: Option[String], kern: Kernel): Resource[F, Span[F]] =
        Resource.makeCase(
          Ref.of[F, List[(String, TraceValue)]](List.empty).map { ref =>
            new TestSpan[F] {
              def tags: F[List[(String, TraceValue)]] = ref.get
              def span(newName: String): Resource[F, Span[F]] = makeSpan(newName, Some(name), kern)
              def put(fields: (String, TraceValue)*): F[Unit] = ref.update(_ ++ fields)
              def traceId: F[Option[String]] = F.pure(None)
              def spanId: F[Option[String]] = F.pure(None)
              def traceUri: F[Option[URI]] = F.pure(None)
              def kernel: F[Kernel] = F.pure(kern)

              override def log(fields: (String, TraceValue)*): F[Unit] = F.unit

              override def log(event: String): F[Unit] = F.unit

              override def attachError(err: Throwable): F[Unit] = F.unit

              override def span(name: String, kernel: Kernel): Resource[F, Span[F]] =
                makeSpan(name, None, kern)
            }
          }
        ) { (span, ec) =>
          for {
            tags <- span.tags
            time <- Clock[F].realTimeInstant
            testSpan = CompletedSpan(tags, parent, time, ec, kern, name)
            _ <- submitted.update(_ :+ testSpan)
          } yield ()
        }

      new TestEntryPoint[F] {
        def spans: F[List[CompletedSpan]] = submitted.get
        def root(name: String): Resource[F, Span[F]] = makeSpan(name, None, Kernel(Map.empty))
        def continue(name: String, k: Kernel): Resource[F, Span[F]] = makeSpan(name, None, k)
        def continueOrElseRoot(name: String, k: Kernel): Resource[F, Span[F]] = makeSpan(name, None, k)
      }
    }
}
