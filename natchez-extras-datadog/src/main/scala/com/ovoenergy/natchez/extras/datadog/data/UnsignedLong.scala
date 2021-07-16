package com.ovoenergy.natchez.extras.datadog.data

import cats.effect.kernel.Sync
import cats.syntax.either._
import io.circe.{Decoder, Encoder}

import java.lang.Long.{parseUnsignedLong, toUnsignedString}
import scala.util.Random

/**
 * Wrapper for unsigned longs to make dealing with them less error prone
 */
case class UnsignedLong(value: Long) extends AnyVal {
  def toString(radix: Int): String = toUnsignedString(value, radix)
}

object UnsignedLong {

  def random[F[_]: Sync]: F[UnsignedLong] =
    Sync[F].delay(UnsignedLong(Random.nextLong()))

  def fromString(string: String, radix: Int): Either[String, UnsignedLong] =
    Either.catchNonFatal(parseUnsignedLong(string, radix)).bimap(_.getMessage, UnsignedLong.apply)

  implicit val decoder: Decoder[UnsignedLong] =
    Decoder.decodeString.emap(fromString(_, radix = 10))

  implicit val encoder: Encoder[UnsignedLong] =
    Encoder.encodeString.contramap(_.toString)
}


