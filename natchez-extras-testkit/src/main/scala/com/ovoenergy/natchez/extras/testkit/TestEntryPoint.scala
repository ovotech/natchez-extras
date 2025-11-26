package com.ovoenergy.natchez.extras.testkit

import cats.effect.kernel.Resource.ExitCase
import cats.effect.{Clock, Ref, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.natchez.extras.testkit.TestEntryPoint.CompletedSpan
import natchez.{EntryPoint, Kernel, Span, TraceValue}

import java.net.URI
import java.time.Instant
import natchez.Tags

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
              override def tags: F[List[(String, TraceValue)]] = ref.get
              override def put(fields: (String, TraceValue)*): F[Unit] = ref.update(_ ++ fields)
              override def traceId: F[Option[String]] = F.pure(None)
              override def spanId: F[Option[String]] = F.pure(None)
              override def traceUri: F[Option[URI]] = F.pure(None)
              override def kernel: F[Kernel] = F.pure(kern)
              override def log(fields: (String, TraceValue)*): F[Unit] = put(fields *)
              override def log(event: String): F[Unit] = log("event" -> TraceValue.StringValue(event))
              override def attachError(err: Throwable, fields: (String, TraceValue)*): F[Unit] =
                put((Tags.error(true) :: fields.toList) *)
              override def span(name: String, options: Span.Options): Resource[F, Span[F]] = span(name)
              private def span(newName: String): Resource[F, Span[F]] = makeSpan(newName, Some(name), kern)
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
        override def root(name: String, options: Span.Options): Resource[F, Span[F]] =
          makeSpan(name, None, Kernel(Map.empty))
        override def continue(name: String, kernel: Kernel, options: Span.Options): Resource[F, Span[F]] =
          makeSpan(name, None, kernel)
        override def continueOrElseRoot(
          name: String,
          kernel: Kernel,
          options: Span.Options
        ): Resource[F, Span[F]] = makeSpan(name, None, kernel)
      }
    }
}
