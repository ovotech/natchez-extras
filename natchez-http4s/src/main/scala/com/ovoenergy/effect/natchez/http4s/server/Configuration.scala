package com.ovoenergy.effect.natchez.http4s.server

import cats.{Applicative, Apply}
import cats.data.Kleisli
import cats.effect.Sync
import cats.kernel.Semigroup
import natchez.TraceValue
import cats.instances.map._
import org.http4s.{Headers, Message, Request, Response}
import cats.syntax.semigroup._
import cats.syntax.functor._
import com.ovoenergy.effect.natchez.http4s.server.Configuration.TagReader
import com.ovoenergy.effect.natchez.http4s.server.Configuration.TagReader._
import natchez.TraceValue.StringValue
import org.http4s.util.{CaseInsensitiveString, StringWriter}

/**
 * The tricky part about putting HTTP4s middleware into a library is that
 * each user is likely to want to extract different tags from their requests
 * according to whatever their organisation wide tagging policy is
 *
 * As such we define how to create tags from Requests / Responses with a `TagReader`
 * which has a semigroup instance, allowing you to cherry pick what you want to extract
 * and write your own extractors if required.
 */
case class Configuration[F[_]](
  request: TagReader[Request[F], F],
  response: TagReader[Either[Throwable, Response[F]], F]
)

object Configuration {

  // just to shorten some lines
  type Tags = Map[String, TraceValue]

  /**
   * A tag reader is essentially a function from an HTTP message to F[Tags]
   * We need the effect because some tags may involve streaming the entity body
   */
  case class TagReader[-A, F[_]](value: Kleisli[F, A, Tags]) extends AnyVal

  object TagReader {

    type MessageReader[F[_]] = TagReader[Message[F], F]
    type RequestReader[F[_]] = TagReader[Request[F], F]
    type ResponseReader[F[_]] = TagReader[Either[Throwable, Response[F]], F]

    /**
     * Semigroup instance for TagReader
     * so it is easy to read many tags from a request
     */
    implicit def sg[M, F[_]: Apply]: Semigroup[TagReader[M, F]] = {
      implicit def applySg[A: Semigroup]: Semigroup[F[A]] = Apply.semigroup[F, A]
      implicit val takeLast: Semigroup[TraceValue] = (_, b) => b
      (a, b) => TagReader[M, F](a.value |+| b.value)
    }

    def message[F[_]: Applicative](f: Message[F] => Tags): MessageReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(f(a))))

    def request[F[_]: Applicative](f: Request[F] => Tags): RequestReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(f(a))))

    def okResponse[F[_]: Applicative](f: Response[F] => Tags): ResponseReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(a.fold(_ => Map.empty, f))))

    def errorResponse[F[_]: Applicative](f: Throwable => Tags): ResponseReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(a.fold(f, _ => Map.empty))))
  }

  private val isSensitive: CaseInsensitiveString => Boolean =
    cs => Headers.SensitiveHeaders.contains(cs) || cs.value.toLowerCase.contains("key")

  /**
   * Only run the given tag extractor if the response was not successful
   * This is useful for adding extra tags in the case of errors
   */
  def ifFailure[F[_]: Applicative](tr: MessageReader[F]): ResponseReader[F] =
    TagReader {
      Kleisli {
        case Right(resp) if !resp.status.isSuccess => tr.value.run(resp)
        case _ => Applicative[F].pure(Map.empty)
      }
    }

  /**
   * ResponseReaders can handle exceptions as well as responses
   * so this lifts a MessageReader into a ResponseReader
   * that only runs when there aren't exceptions
   */
  def ifResponse[F[_]: Applicative](tr: MessageReader[F]): ResponseReader[F] =
    TagReader {
      Kleisli {
        case Right(resp) => tr.value.run(resp)
        case _ => Applicative[F].pure(Map.empty)
      }
    }

  /**
   * Extract headers from the HTTP message, redact sensitive ones
   * and place them into the span with the given tag name separated by newlines
   */
  def headers[F[_]: Applicative](name: String): MessageReader[F] =
    TagReader.message { message =>
      Map(
        name -> StringValue(
          message
            .headers
            .redactSensitive(isSensitive)
            .foldLeft(new StringWriter) { case (sw, h) => h.render(sw).append('\n') }
            .result
        )
      )
    }

  /**
   * Extract the entity from the HTTP message into a strict string
   * and place that into the span. This may not be ideal if you're streaming things.
   */
  def entity[F[_]: Sync](name: String): MessageReader[F] =
    TagReader(
      Kleisli { message =>
        message
          .bodyAsText
          .compile
          .lastOrError
          .map(body => Map(name -> body))
      }
    )

  /**
   * Extract the URI from the request and place it into the Span
   */
  def uri[F[_]: Applicative](name: String): RequestReader[F] =
    TagReader.request(r => Map(name -> r.uri.renderString))

  /**
   * Extract the URI from the request and place it into the Span
   */
  def method[F[_]: Applicative](name: String): RequestReader[F] =
    TagReader.request(r => Map(name -> r.method.name))

  /**
   * create a TagReader that ignores the message and always adds the given value
   * Useful for static tags like environment or team name
   */
  def const[F[_]: Applicative](name: String, value: TraceValue): MessageReader[F] =
    TagReader(Kleisli.pure(Map(name -> value)))

  /**
   * A Tag reader that just returns an empty map
   */
  def noop[F[_]: Applicative]: MessageReader[F] =
    TagReader(Kleisli.pure(Map.empty))

  /**
   * Extract the status code from the response and place it into the Span
   */
  def statusCode[F[_]: Applicative](name: String): ResponseReader[F] =
    TagReader.okResponse(r => Map(name -> r.status.code))

  /**
   * If the HTTP routes return an exception then put its message into the Span
   */
  def exceptionMessage[F[_]: Applicative](name: String): ResponseReader[F] =
    TagReader.errorResponse(e => Map(name -> e.getMessage))

  /**
   * If the HTTP routes return an exception then put its class name into the Span
  */
  def exceptionType[F[_]: Applicative](name: String): ResponseReader[F] =
    TagReader.errorResponse(e => Map(name -> e.getClass.getSimpleName))

  /**
   * If the HTTP routes return an exception then put its stack trace into the Span
   */
  def exceptionStack[F[_]: Applicative](name: String): ResponseReader[F] =
    TagReader.errorResponse(e => Map(name -> e.getStackTrace.mkString("\n")))

  /**
   * Create a default configuration for tracing HTTP4s calls.
   * This uses Datadog tag names but the idea is you can make your own configs with ease.
   */
  def default[F[_]: Sync](defaults: (String, TraceValue)*): Configuration[F] = {
    val static = defaults.foldLeft(noop[F]) { case (f, (k, v)) => f |+| const(k, v) }
    Configuration[F](
      request = uri("http.url") |+|
        headers("http.request.headers") |+|
        method("http.method") |+|
        static,
      response = statusCode("http.status_code") |+|
        ifResponse(headers("http.response.headers")) |+|
        ifFailure(entity("http.response.entity")) |+|
        exceptionMessage("error.msg") |+|
        exceptionType("error.type") |+|
        exceptionStack("error.stack")
    )
  }
}
