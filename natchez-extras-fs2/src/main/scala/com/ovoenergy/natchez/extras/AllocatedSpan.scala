package com.ovoenergy.natchez.extras

import cats.Traverse
import cats.effect.{Concurrent, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import fs2.concurrent.Queue
import fs2.{Pipe, Stream}
import natchez.{Kernel, Span, TraceValue}

import java.net.URI

/**
 * A Natchez span that has been pre-allocated and will stay open
 * until either the stream that created it terminates or you call submit
 */
trait AllocatedSpan[F[_]] extends Span[F] {

  /**
   * Add a task to run on calling submit. The added task will run before the span is submitted.
   * If it fails any errors will be discarded and the span will then be submitted
   */
  def addSubmitTask(task: F[Unit]): AllocatedSpan[F]

  /**
   * Submit the span. The span should be considered invalid after this is called
   * and no subspans should be created
   */
  def submit: F[Unit]
}

object AllocatedSpan {

  /**
   * Given a span broken out of a natchez resource
   * create an AllocatedSpan that allows us to submit it
   */
  private def createSpan[F[_]](
    spn: Span[F],
    submitTask: F[Unit]
  )(implicit F: Sync[F]): AllocatedSpan[F] =
    new AllocatedSpan[F] {
      def kernel: F[Kernel] =
        spn.kernel
      def put(fields: (String, TraceValue)*): F[Unit] =
        spn.put(fields: _*)
      def span(name: String): Resource[F, Span[F]] =
        spn.span(name)
      def addSubmitTask(task: F[Unit]): AllocatedSpan[F] =
        createSpan(spn, F.uncancelable(F.attempt(task) >> submit))
      def submit: F[Unit] =
        submitTask
      def traceId: F[Option[String]] =
        spn.traceId
      def spanId: F[Option[String]] =
        spn.spanId
      def traceUri: F[Option[URI]] =
        spn.traceUri
    }

  /**
   * Associate a value with an allocated span
   */
  case class Traced[F[_], A](
    span: AllocatedSpan[F],
    value: A
  )

  object Traced {
    implicit def traverse[F[_]]: Traverse[Traced[F, *]] =
      cats.derived.semiauto.traverse[Traced[F, *]]
  }

  /**
   * Create an AllocatedSpan which breaks a span out of a Natchez resource
   * and instead allows it to be submitted manually when committing Kafka messages
   */
  def create[F[_]: Concurrent, A](maxOpen: Int = 100)(
    rootSpan: A => Resource[F, Span[F]]
  )(implicit F: Sync[F]): Pipe[F, A, Traced[F, A]] = { stream =>
    /*
     * First we create an outer queue that receives tasks to run and
     * passes them along to parEvalMap so that interruption of the outer stream we're piping
     * will cause all the tasks to be cancelled
     */
    Stream
      .eval(Queue.bounded[F, F[Unit]](maxSize = 1))
      .flatMap { taskQueue =>
        stream
          .concurrently(taskQueue.dequeue.parEvalMapUnordered(maxOpen)(identity))
          .evalMap { item =>
            /*
             * We then create two queues - one to dig the Span out of the resource and another
             * that we use to block the resource from quitting until we say so,
             * either with an error or a unit to indicate success
             */
            for {
              out  <- Queue.bounded[F, Span[F]](maxSize = 1)
              halt <- Queue.bounded[F, Either[Throwable, Unit]](maxSize = 1)
              task = rootSpan(item).use(out.enqueue1(_) >> halt.dequeue1.flatMap(F.fromEither))

              /*
               * we create a task that creates a span, submits it to our out queue
               * and then blocks until we tell it to quit. We then pass that to
               * the task queue  so it'll be cancelled only if the outer stream quits
               */
              _ <- taskQueue.enqueue1(task)

              /*
               * We then wait for the span we've dug out of the resource
               * to come back from the task we've just submitted
               */
              s <- out.dequeue1
            } yield
              Traced(
                value = item,
                span = createSpan(s, halt.enqueue1(Right(())))
              )
          }
      }
  }
}
