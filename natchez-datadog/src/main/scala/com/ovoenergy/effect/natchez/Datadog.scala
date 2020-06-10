package com.ovoenergy.effect.natchez

import cats.Applicative
import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.instances.option._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import com.ovoenergy.effect.natchez.DatadogSpan.SpanNames
import fs2.concurrent.Queue
import io.circe.{Encoder, Printer}
import fs2._
import natchez.{EntryPoint, Kernel, Span}
import org.http4s.Method.PUT
import org.http4s.circe.CirceInstances.builder
import org.http4s.client.Client
import org.http4s.{EntityEncoder, Header, Request, Uri}
import org.slf4j.{Logger, LoggerFactory}
import cats.syntax.apply._

import scala.concurrent.duration._

object Datadog {

  private def logger[F[_]: Sync]: F[Logger] =
    Sync[F].delay(LoggerFactory.getLogger(getClass.getName))

  private def agentEndpoint[F[_]: Sync]: F[Uri] =
    Sync[F]
      .delay(sys.env.getOrElse("DD_AGENT_HOST", "localhost"))
      .flatMap(host => Sync[F].fromEither(Uri.fromString(s"http://$host/v0.3/traces")))

  private def spanQueue[F[_]: Concurrent]: Resource[F, Queue[F, SubmittableSpan]] =
    Resource.liftF(Queue.circularBuffer[F, SubmittableSpan](maxSize = 1000))

  private implicit def encoder[F[_]: Applicative, A: Encoder]: EntityEncoder[F, A] =
    builder.withPrinter(Printer.noSpaces.copy(dropNullValues = true)).build.jsonEncoderOf[F, A]

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
  ): F[Unit] =
    queue
      .tryDequeueChunk1(maxSize = 1000)
      .flatMap { items =>
        items.traverse { traces =>
          Sync[F].attempt(
            client.status(
              Request[F](uri = agentHost, method = PUT)
                .withHeaders(Header("X-DataDog-Trace-Count", traces.size.toString))
                .withEntity(traces.toList.groupBy(_.traceId).values.toList)
            )
          ).flatMap {
            case Left(exception) =>
              Sync[F].delay(logger.warn("Failed to submit to Datadog", exception))
            case Right(status) if !status.isSuccess =>
              Sync[F].delay(logger.warn(s"Got $status from Datadog agent"))
            case Right(status) =>
              Sync[F].delay(logger.debug(s"Got $status from Datadog agent"))
          }
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
    queue: Queue[F, SubmittableSpan]
  ): Resource[F, Unit] =
    Resource.liftF((logger[F], agentEndpoint[F]).tupled).flatMap { case (logger, uri) =>
      val submit: F[Unit] = submitOnce(queue, http, logger, uri)
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
