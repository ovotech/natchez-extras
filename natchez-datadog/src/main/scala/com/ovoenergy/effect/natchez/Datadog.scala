package com.ovoenergy.effect.natchez

import cats.Applicative
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.ovoenergy.effect.natchez.DatadogSpan.{CompletedSpan, SpanNames}
import fs2.concurrent.Queue
import io.circe.{Encoder, Printer}
import fs2._
import natchez.{EntryPoint, Kernel, Span}
import org.http4s.Method.PUT
import org.http4s.circe.CirceInstances.builder
import org.http4s.client.Client
import org.http4s.{EntityEncoder, Request, Uri}

import scala.concurrent.duration._

object Datadog {

  val agentEndpoint: Uri =
    Uri.unsafeFromString("http://localhost:8126/v0.3/traces")

  def spanQueue[F[_]: Concurrent]: Resource[F, Queue[F, CompletedSpan]] =
    Resource.liftF(Queue.circularBuffer[F, CompletedSpan](maxSize = 1000))

  private implicit def encoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] =
    builder.withPrinter(Printer.noSpaces.copy(dropNullValues = true)).build.jsonEncoderOf[F, A]

  /**
   * Submit one list of traces to DataDog
   * we group them up by trace ID but I don't think this is actually required,
   * in that you can submit new spans for existing traces across multiple requests
   * We do this in one `F[_]` operation so it won't be interrupted half way through on shutdown
   */
  private def submitOnce[F[_]: Sync](client: Client[F], queue: Queue[F, CompletedSpan]): F[Unit] =
    queue
      .tryDequeueChunk1(maxSize = 1000)
      .flatMap { items =>
        items.traverse { traces =>
          val grouped = traces.toList.groupBy(_.traceId).values.toList
          val req = Request[F](uri = agentEndpoint, method = PUT).withEntity(grouped)
          client.status(req)
        }
      }
      .as(())

  /**
   * Process to poll the queue and submit items to Datadog periodically,
   * either every 5 items or every 10 seconds, doing this in here is perhaps a bit cheeky.
   * we do one final submit after cancelling the process to drain the queue
   */
  private def submitter[F[_]: Concurrent: Timer](
    http: Client[F],
    queue: Queue[F, CompletedSpan]
  ): Resource[F, Unit] =
    Resource
      .make(
        Semaphore[F](1).flatMap { sem =>
          Concurrent[F]
            .start(
              Stream
                .repeatEval(sem.acquire >> submitOnce(http, queue) >> sem.release)
                .debounce(0.5.seconds)
                .compile
                .drain
            )
            .map(f => sem -> f)
        }
      ) { case (sem, fiber) => sem.acquire >> submitOnce(http, queue) >> fiber.cancel }
      .as(())

  /**
   * Produce an EntryPoint into a Datadog tracing context,
   * kicks off the async publishing process. We don't quite adhere to the spirit of these functions
   * in that `continue` always succeeds but does its best to recreate the trace from HTTP
   */
  def entryPoint[F[_]: Concurrent: Timer: Clock](
    client: Client[F],
    service: String,
    resource: String
  ): Resource[F, EntryPoint[F]] =
    for {
      queue <- spanQueue
      names = SpanNames.withFallback(_, SpanNames("unnamed", service, resource))
      _ <- submitter(client, queue)
    } yield {
      new EntryPoint[F] {
        def root(name: String): Resource[F, Span[F]] =
          Resource.liftF(SpanIdentifiers.create).flatMap(DatadogSpan.create(queue, names(name))).widen
        def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
          DatadogSpan.fromKernel(queue, names(name), kernel).widen
        def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
          DatadogSpan.fromKernel(queue, names(name), kernel).widen
      }
    }
}
