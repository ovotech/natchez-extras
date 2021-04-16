---
layout: docs
title: "Natchez SLF4J"
---


# Natchez SLF4J

The module provides a natchez `EntryPoint` that sends tracing information to an
SLF4J logger. At OVO, we use this to make it easy to see tracing information in
the terminal when running locally. Once you have the `EntryPoint` you can then
use natchez as described in [its
README](https://github.com/tpolecat/natchez/blob/master/README.md).

## Installation

Add this module and an SLF4J binding (in this example we're using
[Logback](http://logback.qos.ch/)) to your `build.sbt`:

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "ch.qos.logback"  %  "logback-classic"      % "1.2.3",
  "com.ovoenergy"   %% "natchez-extras-slf4j" % natchezExtrasVersion
)

```

## Example usage

```scala mdoc
import cats.effect.{ExitCode, IO, IOApp}
import com.ovoenergy.natchez.extras.slf4j.Slf4j
import natchez.EntryPoint

import scala.concurrent.duration._

object MyTracedApp extends IOApp {

  /**
   * Create a Natchez entrypoint that will send traces to SLF4J
   */
  val slf4j: EntryPoint[IO] = Slf4j.entryPoint[IO]

  /**
   * This app creates a root span, adds a tag to set the env to UAT
   * then creates a subspan that sleeps for two seconds
   */
  def run(args: List[String]): IO[ExitCode] =
    slf4j.root("root-span").use { rootSpan =>
      for {
        _ <- rootSpan.put("env" -> "uat")
        _ <- rootSpan.span("child-span").use(_ => IO.sleep(2.seconds))
      } yield ExitCode.Success
    }
}
```

If you run this example, you should see some output like this:

```
sbt> run
[info] Running MyTracedApp
14:14:26.296 [ioapp-compute-0] INFO natchez - root-span started
14:14:26.305 [ioapp-compute-0] INFO natchez - child-span started
14:14:28.318 [ioapp-compute-1] INFO natchez - child-span success
14:14:28.320 [ioapp-compute-1] INFO natchez - root-span success
[success] Total time: 3 s, completed 22-May-2020 14:14:28
```

Although you can see when each trace started and ended, eagle-eyed readers will
notice that the `env` tag we added to the root span is not visible. This is
because span tags get added to the SLF4J MDC (mapped diagnostic context) and by
default Logback doesn't print the MDC to the console.

We can fix this by creating a Logback config file at
`src/main/resources/logback.xml` with the following content (the important part
is that the `<pattern>` element includes the `%mdc` pattern):

```xml
{% raw %}
<?xml version="1.0" encoding="UTF-8" ?>
<configuration>
  <appender name="CONSOLE"
		    class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %level %logger{36} - %msg {%mdc}%n</pattern>
    </encoder>
  </appender>

  <root level="debug">
	<appender-ref ref="CONSOLE" />
  </root>
</configuration>
{% endraw %}
```

Now if we run the code again, we can see the span tags in the console too:

```
[info] Running MyTracedApp
14:31:19.835 [ioapp-compute-0] INFO natchez - root-span started {traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28}
14:31:19.846 [ioapp-compute-0] INFO natchez - child-span started {env=uat, traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28}
14:31:21.859 [ioapp-compute-1] INFO natchez - child-span success {env=uat, traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28}
14:31:21.860 [ioapp-compute-1] INFO natchez - root-span success {env=uat, traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28}
[success] Total time: 3 s, completed 22-May-2020 14:31:21
```
