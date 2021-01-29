---
layout: docs
title: "Natchez FS2"
---

# Natchez FS2

This is one of the more experimental modules on offer. It provides an `AllocatedSpan` which must be manually
submitted on completion rather than a cats `Resource`. This is useful in applications where per-element `Resources` are unwieldy,
i.e. Kafka consumers using FS2. 


## Installation

```scala
val effectUtilsVersion = "@VERSION@"
resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= Seq(
  "com.ovoenergy.effect" %% "natchez-fs2" % effectUtilsVersion
)
```

## Usage

`natchez-fs2` provides an FS2 `Pipe` that given an element in a stream returns it alongside an `AllocatedSpan`.
You can then create subspans for this span as you process the message before manually committing it with `.submit`.

A small `syntax` object provides two functions - `evalMapNamed` and `evalMapTraced` to reduce the boilerplate involved
in unwrapping the message, creating a subspan and processing it.

If the stream is cancelled the span will be closed automatically.

```scala mdoc
import cats.Monad
import cats.effect.{ExitCode, IO, IOApp}
import com.ovoenergy.effect.natchez.syntax._
import com.ovoenergy.effect.natchez.{AllocatedSpan, Slf4j}
import fs2._
import natchez.{EntryPoint, Kernel}

import scala.concurrent.duration._

object NatchezFS2 extends IOApp {

  // a message from e.g. Kafka or SQS.
  case class Message[F[_]](kernel: Kernel, body: String, commit: F[Unit])

  // an infinite stream of messages
  def source[F[_]: Monad]: Stream[F, Message[F]] =
    Stream.emit(Message(Kernel(Map.empty), "test", Monad[F].unit)).repeat

  val entryPoint: EntryPoint[IO] =
    Slf4j.entryPoint[IO]

  def run(args: List[String]): IO[ExitCode] =
    source[IO]
      .through(AllocatedSpan.create()(msg => entryPoint.continueOrElseRoot("consume", msg.kernel)))
      .evalMapNamed("processing-step-1")(m => IO.sleep(1.second).as(m))
      .evalMapNamed("processing-step-2")(m => IO.sleep(2.seconds).as(m))
      .evalMapNamed("commit")(_.commit)
      .evalMap(_.span.submit)
      .compile
      .drain
      .as(ExitCode.Error)
}


```


