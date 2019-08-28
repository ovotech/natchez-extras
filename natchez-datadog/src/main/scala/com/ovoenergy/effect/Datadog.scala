package com.ovoenergy.effect

import cats.Applicative
import cats.effect._
import cats.instances.option._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.ovoenergy.effect.DatadogSpan.CompletedSpan
import fs2.concurrent.Queue
import io.circe.{Encoder, Printer}
import fs2._
import natchez.{EntryPoint, Kernel, Span}
import org.http4s.Method.PUT
import org.http4s.circe.CirceInstances.builder
import org.http4s.client.Client
import org.http4s.{EntityEncoder, Request, Uri}
import cats.syntax.flatMap._

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
    */
  private def submitOnce[F[_]: Sync](client: Client[F], queue: Queue[F, CompletedSpan]): Stream[F, Unit] =
    Stream.bracket(queue.tryDequeueChunk1(maxSize = 1000)) { items =>
      items.traverse { traces =>
        val grouped = traces.toList.groupBy(_.traceId).values.toList
        val req = Request[F](uri = agentEndpoint, method = PUT).withEntity(grouped)
        client.status(req)
      }.as(())
    }.as(())


  /**
   * Process to poll the queue and submit items to Datadog periodically,
   * either every 5 items or every 10 seconds, doing this in here is perhaps a bit cheeky.
   * We use Stream.bracket to ensure any dequeued events will definitely be submitted
   * and we do one final submit after cancelling the process to drain the queue
   */
  private def submitter[F[_]: Concurrent: Timer](
    http: Client[F],
    queue: Queue[F, CompletedSpan]
  ): Resource[F, Unit] =
    Resource.make(
      Concurrent[F].start(submitOnce(http, queue).repeat.debounce(0.5.seconds).compile.drain)
    )(_.cancel >> submitOnce(http, queue).compile.drain).as(())

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
