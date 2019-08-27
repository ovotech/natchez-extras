package com.ovoenergy.effect

import cats.Applicative
import cats.effect._
import cats.syntax.functor._
import com.ovoenergy.effect.DatadogSpan.CompletedSpan
import fs2.concurrent.Queue
import io.circe.{Encoder, Printer}
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
   * Process to poll the queue and submit items to Datadog periodically,
   * either every 5 items or every 10 seconds, doing this in here is perhaps a bit cheeky
   */
  def submitter[F[_]: Concurrent: Timer](
    http: Client[F],
    queue: Queue[F, CompletedSpan]
  ): Resource[F, Unit] =
    Resource.make(
      Concurrent[F].start(
        queue.dequeue
          .groupWithin(5, 5.seconds)
          .evalMap { traces =>
            val grouped = traces.toList.groupBy(_.traceId).values.toList
            val req = Request[F](uri = agentEndpoint, method = PUT).withEntity(grouped)
            http.status(req).as(())
          }.compile.drain
      )
    )(_.cancel).as(())

  /**
   * Produce an EntryPoint into a Datadog tracing context,
   * kicks off the async publishing process. We don't quite adhere to the spirit of these functions
   * in that `continue` always succeeds but does its best to recreate the trace from HTTP
   */
  def entryPoint[F[_]: Concurrent: Timer: Clock](
    client: Client[F],
    service: String
  ): Resource[F, EntryPoint[F]] =
    for {
      queue <- spanQueue
      _ <- submitter(client, queue)
    } yield {
        new EntryPoint[F] {
          def root(name: String): Resource[F, Span[F]] =
            Resource.liftF(SpanIdentifiers.create).flatMap(DatadogSpan.create(queue, name, service)).widen
          def continue(name: String, kernel: Kernel): Resource[F, Span[F]] =
            DatadogSpan.fromKernel(queue, name, service, kernel).widen
          def continueOrElseRoot(name: String, kernel: Kernel): Resource[F, Span[F]] =
            DatadogSpan.fromKernel(queue, name, service, kernel).widen
        }
    }
}
