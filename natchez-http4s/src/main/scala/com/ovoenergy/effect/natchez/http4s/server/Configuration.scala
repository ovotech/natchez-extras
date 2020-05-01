package com.ovoenergy.effect.natchez.http4s.server

import cats.Applicative
import cats.data.Kleisli
import cats.effect.Sync
import cats.instances.list._
import cats.instances.map._
import cats.kernel.{Monoid, Semigroup}
import cats.syntax.foldable._
import cats.syntax.functor._
import cats.syntax.monoid._
import com.ovoenergy.effect.natchez.http4s.server.Configuration.TagReader._
import natchez.TraceValue
import natchez.TraceValue.StringValue
import org.http4s.util.{CaseInsensitiveString, StringWriter}
import org.http4s.{Headers, Message, Request, Response}

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
  request: RequestReader[F],
  response: ResponseReader[F]
)

object Configuration {

  // just to shorten some lines
  type Tags = Map[String, TraceValue]

  /**
   * A tag reader is essentially a function from an HTTP message to F[Tags]
   * We need the effect because some tags may involve streaming the entity body
   */
  case class TagReader[F[_], -A](value: Kleisli[F, A, Tags]) extends AnyVal

  object TagReader {

    type MessageReader[F[_]] = TagReader[F, Message[F]]
    type RequestReader[F[_]] = TagReader[F, Request[F]]
    type ResponseReader[F[_]] = TagReader[F, Response[F]]

    /**
     * Monoid instance for TagReader
     * so it is easy to read many tags from a request
     */
    implicit def monoid[F[_]: Applicative, A]: Monoid[TagReader[F, A]] = {
      implicit def underlying[B: Monoid]: Monoid[F[B]] = Applicative.monoid[F, B]
      implicit val takeLast: Semigroup[TraceValue] = (_, b) => b

      new Monoid[TagReader[F, A]] {
        def empty: TagReader[F, A] =
          TagReader(Monoid[Kleisli[F, A, Map[String, TraceValue]]].empty)
        def combine(x: TagReader[F, A], y: TagReader[F, A]): TagReader[F, A] =
          TagReader(x.value |+| y.value)
      }
    }

    def message[F[_]: Applicative](f: Message[F] => Tags): MessageReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(f(a))))

    def request[F[_]: Applicative](f: Request[F] => Tags): RequestReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(f(a))))

    def response[F[_]: Applicative](f: Response[F] => Tags): ResponseReader[F] =
      TagReader(Kleisli(a => Applicative[F].pure(f(a))))
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
        case resp if !resp.status.isSuccess => tr.value.run(resp)
        case _                              => Applicative[F].pure(Map.empty)
      }
    }

  /**
   * Extract headers from the HTTP message, redact sensitive ones
   * and place them into the span with the given tag name separated by newlines
   */
  def headers[F[_]: Applicative](name: String)(
    redact: CaseInsensitiveString => Boolean
  ): MessageReader[F] =
    TagReader.message { message =>
      Map(
        name -> StringValue(
          message.headers
            .redactSensitive(redact)
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
        message.bodyAsText.compile.last
          .map { body =>
            body.map(b => name -> StringValue(b)).toMap
          }
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
    TagReader.response(r => Map(name -> r.status.code))

  /**
   * Create a default configuration for tracing HTTP4s calls.
   * This uses Datadog tag names but the idea is you can make your own configs with ease.
   */
  def default[F[_]: Sync](defaults: (String, TraceValue)*): Configuration[F] = {
    val static = defaults.toList.foldMap { case (k, v) => const[F](k, v) }
    Configuration[F](
      request = uri[F]("http.url") |+|
      headers("http.request.headers")(isSensitive) |+|
      const("span.type", "web") |+|
      method("http.method") |+|
      static,
      response = statusCode[F]("http.status_code") |+|
      headers("http.response.headers")(isSensitive) |+|
      ifFailure(entity("http.response.entity"))
    )
  }
}
