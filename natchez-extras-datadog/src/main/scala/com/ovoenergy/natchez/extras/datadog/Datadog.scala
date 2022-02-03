package com.ovoenergy.natchez.extras.datadog

import cats.Monad
import cats.effect.kernel.Ref
import cats.effect.std.{Queue, Semaphore}
import cats.effect.{Async, Concurrent, Resource, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.ovoenergy.natchez.extras.datadog.DatadogSpan.SpanNames
import fs2.Stream
import io.circe.{Encoder, Printer}
import natchez.{EntryPoint, Kernel, Span}
import org.http4s.Method.PUT
import org.http4s.Uri.Path.unsafeFromString
import org.http4s.circe.CirceInstances.builder
import org.http4s.client.Client
import org.http4s.syntax.literals._
import org.http4s.{EntityEncoder, Request, Uri}
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.duration._

object Datadog {

  private def logger[F[_]: Sync]: F[Logger] =
    Sync[F].delay(LoggerFactory.getLogger(getClass.getName))

  private def spanQueue[F[_]: Concurrent]: Resource[F, Queue[F, SubmittableSpan]] =
    Resource.eval(Queue.circularBuffer[F, SubmittableSpan](capacity = 1000))

  private implicit def encoder[F[_], A: Encoder]: EntityEncoder[F, A] =
    builder.withPrinter(Printer.noSpaces.copy(dropNullValues = true)).build.jsonEncoderOf

  /**
   * Take items from the queue until it blocks
   * TODO I feel this must exist in FS2 somewhere
   */
  private def takeWhileAvailable[F[_]: Monad, A](queue: Queue[F, A], max: Int): F[Vector[A]] =
    Monad[F].tailRecM[Vector[A], Vector[A]](Vector.empty) { list =>
      queue.tryTake.map {
        case None                                 => Right(list)
        case Some(item) if list.length >= max - 1 => Right(list :+ item)
        case Some(item)                           => Left(list :+ item)
      }
    }

  /**
   * Submit one list of traces to DataDog
   * we group them up by trace ID but I don't think this is actually required,
   * in that you can submit new spans for existing traces across multiple requests
   * We do this in one `F[_]` operation so it won't be interrupted half way through on shutdown
   */
  private def submitOnce[F[_]: Sync](
    queue: Queue[F, SubmittableSpan],
    client: Client[F],
    logger: Logger,
    agentHost: Uri
  ): F[Unit] = {
    takeWhileAvailable(queue, max = 1000)
      .flatMap {
        case Vector() =>
          Sync[F].unit
        case traces =>
          Sync[F]
            .attempt(
              client.status(
                Request[F](uri = agentHost.withPath(unsafeFromString("/v0.3/traces")), method = PUT)
                  .withHeaders("X-DataDog-Trace-Count" -> traces.length.toString)
                withEntity (traces.groupBy(_.traceId).values.toList)
              )
            )
            .flatMap {
              case Left(exception) =>
                Sync[F].delay(logger.warn("Failed to submit to Datadog", exception))
              case Right(status) if !status.isSuccess =>
                Sync[F].delay(logger.warn(s"Got $status from Datadog agent"))
              case Right(status) =>
                Sync[F].delay(logger.debug(s"Got $status from Datadog agent"))
            }
      }
      .as(())
  }

  /**
   * Process to poll the queue and submit items to Datadog periodically,
   * either every 5 items or every 10 seconds, doing this in here is perhaps a bit cheeky.
   * we do one final submit after cancelling the process to drain the queue
   */
  private def submitter[F[_]: Async](
    http: Client[F],
    agent: Uri,
    queue: Queue[F, SubmittableSpan]
  ): Resource[F, Unit] =
    Resource.eval(logger[F]).flatMap { logger =>
      val submit: F[Unit] = submitOnce(queue, http, logger, agent)
      Resource
        .make(
          Semaphore[F](1).flatMap { sem =>
            Concurrent[F]
              .start(
                Stream
                  .repeatEval(sem.acquire >> submit >> sem.release)
                  .metered(0.5.seconds)
                  .compile
                  .drain
              )
              .map(f => sem -> f)
          }
        ) { case (sem, fiber) => sem.acquire >> submit >> fiber.cancel }
        .as(())
    }

  /**
   * Produce an EntryPoint into a Datadog tracing context,
   * kicks off the async publishing process. We don't quite adhere to the spirit of these functions
   * in that `continue` always succeeds but does its best to recreate the trace from HTTP
   */
  def entryPoint[F[_]: Async](
    client: Client[F],
    service: String,
    resource: String,
    agentHost: Uri = uri"http://localhost:8126"
  ): Resource[F, EntryPoint[F]] =
    for {
      queue <- spanQueue
      names = SpanNames.withFallback(_, SpanNames("unnamed", service, resource))
      _ <- submitter(client, agentHost, queue)
    } yield {
      new EntryPoint[F] {
        def root(name: String): Resource[F, Span[F]] =
          Resource
            .eval(SpanIdentifiers.create.flatMap(Ref.of[F, SpanIdentifiers]))
            .flatMap(DatadogSpan.create(queue, names(name)))
            .widen

        def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
          DatadogSpan.fromKernel(queue, names(name), kernel).widen

        def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
          DatadogSpan.fromKernel(queue, names(name), kernel).widen
      }
    }
}
