// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [      
    {
      "title": "Metrics",
      "url": "/natchez-extras/docs/",
      "content": "Datadog Metrics This module provides a Metrics[F] with Events[F] trait that lets you send counters, histograms and events to the Datadog agent over UDP. Note that this means you are unlikely to receive errors if the Datadog agent isn’t reachable! For more details about counters, histograms &amp; the UDP format see the Datadog DogStatsD documentation. For more details about events see the event documentation. Installation In your build.sbt libraryDependencies += \"com.ovoenergy\" %% \"natchez-extras-dogstatsd\" % \"4.0.1\" Example usage import java.net.InetSocketAddress import cats.effect.{ExitCode, IO, IOApp} import com.ovoenergy.natchez.extras.dogstatsd.Events.{AlertType, Priority} import com.ovoenergy.natchez.extras.metrics.Metrics.Metric import com.ovoenergy.natchez.extras.metrics.Metrics import com.ovoenergy.natchez.extras.dogstatsd.{Dogstatsd, Events} object MetricApp extends IOApp { val metricConfig: Dogstatsd.Config = Dogstatsd.Config( // adds a prefix to all metrics, e.g. `my_app.metricname` metricPrefix = Some(\"my_app\"), // the address your Datadog agent is listening on agentHost = new InetSocketAddress(\"localhost\", 8125), // these tags will be added to all metrics + events globalTags = Map(\"example_tag\" -&gt; \"example_value\") ) val exampleEvent: Events.Event = Events.Event( title = \"Gosh, an event just ocurred!\", body = \"You should investigate this right away\", alertType = AlertType.Warning, priority = Priority.Normal, tags = Map.empty ) val exampleCounter: Metric = Metric(name = \"my_counter\", tags = Map.empty) val exampleHistogram: Metric = Metric(name = \"my_histogram\", tags = Map.empty) def run(args: List[String]): IO[ExitCode] = Dogstatsd[IO, IO](metricConfig).use { metrics: Metrics[IO] with Events[IO] =&gt; for { _ &lt;- metrics.counter(exampleCounter)(1) _ &lt;- metrics.histogram(exampleHistogram)(1) _ &lt;- metrics.event(exampleEvent) } yield ExitCode.Success } }"
    } ,    
    {
      "title": "Natchez Combine",
      "url": "/natchez-extras/docs/natchez-combine.html",
      "content": "Natchez Combine natchez-extras-combine is a module that allows you to combine two Natchez EntryPoints into one, allowing you to send tracing information to more than one destination. At OVO we use this module to send traces both to Datadog and also to STDOUT (via natchez-slf4j) so when running applications locally we can get a sense of what is going on without having to leave the terminal. Installation In your build.sbt val http4sVersion = \"0.21.4\" val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"org.http4s\" %% \"http4s-blaze-client\" % http4sVersion, \"com.ovoenergy\" %% \"natchez-extras-combine\" % natchezExtrasVersion, \"com.ovoenergy\" %% \"natchez-extras-datadog\" % natchezExtrasVersion, \"com.ovoenergy\" %% \"natchez-extras-slf4j\" % natchezExtrasVersion ) Example usage: This example combines natchez-extras-datadog and natchez-extras-slf4j hence the extra dependencies import com.ovoenergy.natchez.extras.combine.Combine import com.ovoenergy.natchez.extras.slf4j.Slf4j import com.ovoenergy.natchez.extras.datadog.Datadog import org.http4s.client.blaze.BlazeClientBuilder import natchez.EntryPoint import cats.effect.IO import cats.effect.Resource import cats.effect.ExitCode import cats.effect.IOApp import scala.concurrent.ExecutionContext.global object MyTracedApp extends IOApp { /** * Create a Natchez entrypoint that will log when spans begin and end * This is useful when running the application locally */ val slf4j: EntryPoint[IO] = Slf4j.entryPoint[IO] /** * Create a Natchez entrypoint that will send traces to Datadog */ val datadog: Resource[IO, EntryPoint[IO]] = for { httpClient &lt;- BlazeClientBuilder[IO](global).withDefaultSslContext.resource entryPoint &lt;- Datadog.entryPoint(httpClient, \"service\", \"resource\") } yield entryPoint /** * Use natchez-combine to send traces to both SLF4J &amp; Datadog * This is what you'll then use for the rest of the application */ val combined: Resource[IO, EntryPoint[IO]] = datadog.map { dd =&gt; Combine.combine(dd, slf4j) } def run(args: List[String]): IO[ExitCode] = combined.use { _: EntryPoint[IO] =&gt; IO.never } // this is the bit you have to do }"
    } ,    
    {
      "title": "Natchez Datadog",
      "url": "/natchez-extras/docs/natchez-datadog.html",
      "content": "Natchez Datadog This module provides a natchez EntryPoint that sends tracing information to Datadog. Once you’ve got the EntryPoint you can then use Natchez as described in its README Configuring the agent Depending on how you’re using the Datadog agent you may need to set some configuration values to enable the APM. Details can be found on the Datadog website natchez-extras-datadog currently expects the agent to be reachable over HTTP at http://localhost:8126 - if you’re running the agent in a docker container this should typically be the case. Installation natchez-extras-datadog uses HTTP4s to submit traces to the Datadog trace API, hence the need for http4s-blaze-client. val http4sVersion = \"0.21.4\" val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"org.http4s\" %% \"http4s-blaze-client\" % http4sVersion, \"com.ovoenergy\" %% \"natchez-extras-datadog\" % natchezExtrasVersion, \"com.ovoenergy\" %% \"natchez-extras-slf4j\" % natchezExtrasVersion ) Example usage import cats.effect.{ExitCode, IO, IOApp, Resource} import com.ovoenergy.natchez.extras.datadog.Datadog import natchez.EntryPoint import org.http4s.client.blaze.BlazeClientBuilder import scala.concurrent.ExecutionContext.global import scala.concurrent.duration._ object MyTracedApp extends IOApp { /** * Create a Natchez entrypoint that will send traces to Datadog */ val datadog: Resource[IO, EntryPoint[IO]] = for { httpClient &lt;- BlazeClientBuilder[IO](global).withDefaultSslContext.resource entryPoint &lt;- Datadog.entryPoint(httpClient, \"example-service\", \"example-resource\") } yield entryPoint /** * This app creates a root span, adds a tag to set the env to UAT * then creates a subspan that sleeps for two seconds */ def run(args: List[String]): IO[ExitCode] = datadog.use { entryPoint: EntryPoint[IO] =&gt; entryPoint.root(\"root-span\").use { rootSpan =&gt; for { _ &lt;- rootSpan.put(\"env\" -&gt; \"uat\") _ &lt;- rootSpan.span(\"child-span\").use(_ =&gt; IO.sleep(2.seconds)) } yield ExitCode.Success } } } Running this should yield a trace in Datadog: Naming spans and traces Spans in Natchez are identified by a single name while in Datadog spans are identified by a service, resource, and name. You should ensure all your root spans have the same name so they’ll all show up in the Datadog UI. You can set a default service &amp; resource when creating the Datadog EntryPoint. If you want to change either value for a particular span you can pass the new values into the span name as a colon separated string: &lt;service&gt;:&lt;resource&gt;:&lt;name&gt; to set everything &lt;resource&gt;:&lt;name&gt; to keep the service of the parent span &lt;name&gt; to keep the service &amp; resource of the parent span Datadog specific tags A number of helper functions to create tags that Datadog uses to drive its trace UI can be found in DatadogTags.scala. An example of how to use them follows: import com.ovoenergy.natchez.extras.datadog.DatadogTags._ import natchez.Trace object DatadogTagsExample { def addTags[F[_]](implicit F: Trace[F]): F[Unit] = F.put( /** * This controls how the span is labelled in the Datadog trace UI * Valid values for this are \"Web\", \"Cache\", \"Db\" or \"Custom\" (the default) */ spanType(SpanType.Web), /** * These appear in the trace UI alongside spans * 200 status codes appear green for example */ httpStatusCode(200), httpMethod(\"GET\"), httpUrl(\"http://localhost\"), /** * I'm not actually sure where this appears in the UI * but I am given to believe that it does somewhere */ sqlQuery(\"SELECT foo FROM bar\"), /** * If your span fails these will be highlighted in red in the UI. * These tags will automatically be added to failed spans by natchez-datadog. */ errorMessage(\"Something went wrong\"), errorStack(new Exception().getStackTrace.mkString(\"\\n\")) ) }"
    } ,    
    {
      "title": "Natchez Doobie",
      "url": "/natchez-extras/docs/natchez-doobie.html",
      "content": "Natchez Doobie natchez-extras-doobie provides a Transactor that adds spans for database queries to your traces. Installation val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"com.ovoenergy\" %% \"natchez-extras-doobie\" % natchezExtrasVersion ) Usage If you want to run this example yourself you can use Docker to spin up a temporary Postgres instance: docker run -d -p5432:5432 -e\"POSTGRES_PASSWORD=password\" -e\"POSTGRES_USER=postgres\" postgres This example demonstrates connecting to a database with Doobie, wrapping the transactor into a TracedTransactor and then passing that into a tagless final application that queries the database. import cats.data.Kleisli import cats.effect.{Blocker, ExitCode, IO, IOApp, Resource, Sync} import cats.syntax.functor._ import com.ovoenergy.natchez.extras.datadog.Datadog import com.ovoenergy.natchez.extras.doobie.TracedTransactor import doobie.implicits._ import doobie.util.transactor.Transactor import natchez.{EntryPoint, Span, Trace} import org.http4s.client.blaze.BlazeClientBuilder import scala.concurrent.ExecutionContext.global object NatchezDoobie extends IOApp { type TracedIO[A] = Kleisli[IO, Span[IO], A] /** * Create a Natchez entrypoint that will send traces to Datadog */ val datadog: Resource[IO, EntryPoint[IO]] = for { httpClient &lt;- BlazeClientBuilder[IO](global).withDefaultSslContext.resource entryPoint &lt;- Datadog.entryPoint(httpClient, \"example-database\", \"default-resource\") } yield entryPoint /** * Create a Doobie transactor that connects to a preexisting postgres instance * and then wrap it in TracedTransactor so it creates spans for queries */ val transactor: Resource[IO, Transactor[TracedIO]] = Blocker[IO].map { blocker =&gt; TracedTransactor( service = \"my-example-service-db\", blocking = blocker, transactor = Transactor.fromDriverManager[IO]( driver = \"org.postgresql.Driver\", url = \"jdbc:postgresql:example\", user = \"postgres\", pass = \"password\" // of course don't hard code these details in your applications! ) ) } /** * Your application code doesn't need to know about the TracedIO type, * it just works with an effect type that has a Trace instance */ def application[F[_]: Sync: Trace](db: Transactor[F]): F[ExitCode] = sql\"SELECT * FROM example\" .query[String] .to[List] .transact(db) .map(println) .as(ExitCode.Success) /** * To run the application we create a root span * and use that to turn the application from a TracedIO into an IO */ def run(args: List[String]): IO[ExitCode] = datadog.use { entryPoint =&gt; entryPoint.root(\"root_span\").use { root =&gt; transactor.use { db =&gt; application(db).run(root) } } } } Upon running this code you should see a trace like this show up in Datadog. Note that -db is automatically appended to the service name you provide TracedTransactor."
    } ,    
    {
      "title": "Natchez FS2",
      "url": "/natchez-extras/docs/natchez-fs2.html",
      "content": "Natchez FS2 This is one of the more experimental modules on offer. It provides an AllocatedSpan which must be manually submitted on completion rather than a cats Resource. This is useful in applications where per-element Resources are unwieldy, i.e. Kafka consumers using FS2. Installation val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"com.ovoenergy\" %% \"natchez-extras-fs2\" % natchezExtrasVersion ) Usage natchez-extras-fs2 provides an FS2 Pipe that given an element in a stream returns it alongside an AllocatedSpan. You can then create subspans for this span as you process the message before manually committing it with .submit. A small syntax object provides two functions - evalMapNamed and evalMapTraced to reduce the boilerplate involved in unwrapping the message, creating a subspan and processing it. If the stream is cancelled the span will be closed automatically. import cats.Monad import cats.effect.{ExitCode, IO, IOApp} import com.ovoenergy.natchez.extras.fs2.syntax._ import com.ovoenergy.natchez.extras.fs2.AllocatedSpan import com.ovoenergy.natchez.extras.slf4j.Slf4j import fs2._ import natchez.{EntryPoint, Kernel} import scala.concurrent.duration._ object NatchezFS2 extends IOApp { // a message from e.g. Kafka or SQS. case class Message[F[_]](kernel: Kernel, body: String, commit: F[Unit]) // an infinite stream of messages def source[F[_]: Monad]: Stream[F, Message[F]] = Stream.emit(Message(Kernel(Map.empty), \"test\", Monad[F].unit)).repeat val entryPoint: EntryPoint[IO] = Slf4j.entryPoint[IO] def run(args: List[String]): IO[ExitCode] = source[IO] .through(AllocatedSpan.create()(msg =&gt; entryPoint.continueOrElseRoot(\"consume\", msg.kernel))) .evalMapNamed(\"processing-step-1\")(m =&gt; IO.sleep(1.second).as(m)) .evalMapNamed(\"processing-step-2\")(m =&gt; IO.sleep(2.seconds).as(m)) .evalMapNamed(\"commit\")(_.commit) .evalMap(_.span.submit) .compile .drain .as(ExitCode.Error) }"
    } ,    
    {
      "title": "Natchez HTTP4s",
      "url": "/natchez-extras/docs/natchez-http4s.html",
      "content": "Natchez HTTP4s natchez-extras-http4s provides HTTP4s Middleware to trace all HTTP requests. At the time of writing there is a PR on Natchez itself that will provide this functionality. When it is merged this module will continue to exist but as a wrapper that adds tags used by Datadog. Installation val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"com.ovoenergy\" %% \"natchez-extras-http4s\" % natchezExtrasVersion ) Usage To use Natchez HTTP4s you create an HttpApp[Kleisli[F, Span[F], *]] (i.e. an HttpApp that requires a span to run) and pass it into TraceMiddleware to obtain an HttpApp[F] you can then run normally. import cats.data.Kleisli import cats.effect.{ExitCode, IO, IOApp, Resource, Sync, Timer} import cats.syntax.flatMap._ import cats.syntax.functor._ import com.ovoenergy.natchez.extras.datadog.Datadog import com.ovoenergy.natchez.extras.http4s.Configuration import com.ovoenergy.natchez.extras.http4s.server.TraceMiddleware import natchez.{EntryPoint, Span, Trace} import org.http4s.{HttpApp, HttpRoutes} import org.http4s.client.blaze.BlazeClientBuilder import org.http4s.dsl.Http4sDsl import org.http4s.server.blaze.BlazeServerBuilder import org.http4s.syntax.kleisli._ import scala.concurrent.ExecutionContext.global import scala.concurrent.duration._ object NatchezHttp4s extends IOApp { /** * An example API with a simple GET endpoint * and a POST endpoint that does a few sub operations */ def createRoutes[F[_]: Trace: Sync: Timer]: HttpRoutes[F] = { val dsl = Http4sDsl[F] import dsl._ HttpRoutes.of { case GET -&gt; Root =&gt; Ok(\"Well done\") case POST -&gt; Root =&gt; for { _ &lt;- Trace[F].span(\"operation-1\")(Timer[F].sleep(10.millis)) _ &lt;- Trace[F].span(\"operation-2\")(Timer[F].sleep(50.millis)) res &lt;- Created(\"Thanks\") } yield res } } /** * Create a Natchez entrypoint that will send traces to Datadog */ val datadog: Resource[IO, EntryPoint[IO]] = for { httpClient &lt;- BlazeClientBuilder[IO](global).withDefaultSslContext.resource entryPoint &lt;- Datadog.entryPoint(httpClient, \"example-http-api\", \"default-resource\") } yield entryPoint def run(args: List[String]): IO[ExitCode] = datadog.use { entryPoint =&gt; /** * Our routes need a Trace instance to create spans etc * and the only type that has a trace instance is a Kleisli */ type TracedIO[A] = Kleisli[IO, Span[IO], A] val tracedRoutes: HttpApp[TracedIO] = createRoutes[TracedIO].orNotFound /** * We then apply the TraceMiddleware to the routes to obtain an `HttpApp[IO]`. * The middleware will create traces for each incoming request. */ val routes: HttpApp[IO] = TraceMiddleware[IO](entryPoint, Configuration.default())(tracedRoutes) /** * We can then serve the routes as normal */ BlazeServerBuilder[IO](global) .bindHttp(8080, \"0.0.0.0\") .withHttpApp(routes) .withoutBanner .serve .compile .lastOrError } } Running the above app and hitting the POST endpoint should generate a trace like this: Tracing only some routes Often you don’t want to trace all of your routes, for example if you have a healthcheck route that is polled by a load balancer every few seconds you may wish to exclude it from your traces. You can do this using .fallthroughTo provided in the syntax package which allows the combination of un-traced HttpRoutes[F] and the HttpApp[F] that the tracing middleware returns: import cats.data.Kleisli import cats.effect.{ExitCode, IO, IOApp, Resource} import com.ovoenergy.natchez.extras.datadog.Datadog import com.ovoenergy.natchez.extras.http4s.Configuration import com.ovoenergy.natchez.extras.http4s.server.TraceMiddleware import com.ovoenergy.natchez.extras.http4s.server.syntax.KleisliSyntax import natchez.{EntryPoint, Span} import org.http4s._ import org.http4s.client.blaze.BlazeClientBuilder import org.http4s.dsl.io._ import org.http4s.server.blaze.BlazeServerBuilder import org.http4s.syntax.kleisli._ import scala.concurrent.ExecutionContext.global object Main extends IOApp { type TraceIO[A] = Kleisli[IO, Span[IO], A] val conf: Configuration[IO] = Configuration.default() val datadog: Resource[IO, EntryPoint[IO]] = for { httpClient &lt;- BlazeClientBuilder[IO](global).withDefaultSslContext.resource entryPoint &lt;- Datadog.entryPoint(httpClient, \"example-http-api\", \"default-resource\") } yield entryPoint val healthcheck: HttpRoutes[IO] = HttpRoutes.of { case GET -&gt; Root / \"health\" =&gt; Ok(\"healthy\") } val application: HttpRoutes[TraceIO] = HttpRoutes.pure(Response(status = Status.InternalServerError)) def run(args: List[String]): IO[ExitCode] = datadog.use { entryPoint =&gt; val combinedRoutes: HttpApp[IO] = healthcheck.fallthroughTo(TraceMiddleware(entryPoint, conf)(application.orNotFound)) BlazeServerBuilder[IO](global) .withHttpApp(combinedRoutes) .bindHttp(port = 8080) .serve .compile .lastOrError } } Configuration Given that every HTTP API is likely to have different tracing requirements natchez-http4s attempts to be as configurable as possible. The Configuration object passed to TraceMiddleware defines how to turn an HTTP requests and responses into Natchez tags. By default it is set up to create tags suitable for Datadog but you can use the helper functions in Configuration to create your own configs: import cats.effect.IO import com.ovoenergy.natchez.extras.http4s.Configuration import com.ovoenergy.natchez.extras.http4s.Configuration.TagReader._ import natchez.TraceValue.BooleanValue import cats.syntax.semigroup._ object CustomConfigExample { /** * Describe what we want to read from request and put as tags into the span. * This configuration only adds the url and the method. You can use `|+|` to combine * together configurations. */ val customRequestConfig: RequestReader[IO] = Configuration.uri[IO](\"http_request_url\") |+| Configuration.method[IO](\"http_method\") /** * Describe what to read from the HTTP response generated by the app and put into tags. * This configuration won't read anything but will put failed: true if the response is not a 2xx */ val customResponseConfig: ResponseReader[IO] = Configuration.ifFailure(Configuration.const(\"failed\", BooleanValue(true))) /** * The request &amp; response configurations are combined together into this case class * which can then be passed to `TraceMiddleware` */ val customConfig: Configuration[IO] = Configuration( request = customRequestConfig, response = customResponseConfig ) }"
    } ,    
    {
      "title": "Natchez Log4Cats",
      "url": "/natchez-extras/docs/natchez-log4cats.html",
      "content": "Natchez Log4Cats This module provides a wrapper for a StructuredLogger[F] that automatically adds a trace ID &amp; span ID to logs so they will show up alongside traces in Datadog. For this to work you’ll need to configure your logging framework so it sends logs to Datadog. Information about how to do this can be found in the Datadog documentation. While they reccomend logging to a file we’ve not experienced any issues sending logs straight to them as described in the agentless logging section. Installation val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"com.ovoenergy\" %% \"natchez-extras-log4cats\" % natchezExtrasVersion ) Usage import cats.Functor import cats.data.Kleisli import cats.effect.{ExitCode, IO, IOApp, Resource} import com.ovoenergy.natchez.extras.log4cats.TracedLogger import com.ovoenergy.natchez.extras.datadog.Datadog import org.typelevel.log4cats.StructuredLogger import org.typelevel.log4cats.slf4j.Slf4jLogger import natchez.{EntryPoint, Span, Trace} import org.http4s.client.blaze.BlazeClientBuilder import cats.syntax.functor._ import scala.concurrent.ExecutionContext.global object NatchezLog4Cats extends IOApp { type TracedIO[A] = Kleisli[IO, Span[IO], A] /** * Create a Natchez entrypoint that will send traces to Datadog */ val datadog: Resource[IO, EntryPoint[IO]] = for { httpClient &lt;- BlazeClientBuilder[IO](global).withDefaultSslContext.resource entryPoint &lt;- Datadog.entryPoint(httpClient, \"example-service\", \"default-resource\") } yield entryPoint /** * Use Log4Cats-Slf4j to create a StructuredLogger we can then pass to TracedLogger * to produce a logger that automatically adds the trace ID to the MDC */ val logger: IO[StructuredLogger[TracedIO]] = Slf4jLogger.create[IO].map(TracedLogger.lift(_)) /** * The application that uses the logger can depend on it * without knowing that it is a TracedLogger */ def application[F[_]: Functor: Trace: StructuredLogger]: F[ExitCode] = StructuredLogger[F].info(\"I am running!\").as(ExitCode.Success) def run(args: List[String]): IO[ExitCode] = datadog.use { entryPoint =&gt; logger.flatMap { implicit log =&gt; entryPoint.root(\"root_span\").use(application[TracedIO].run) } } } Assuming your logger is configured correctly you should then be able to see the log entry alongside the trace in Datadog: Datadog trace"
    } ,    
    {
      "title": "Natchez SLF4J",
      "url": "/natchez-extras/docs/natchez-slf4j.html",
      "content": "Natchez SLF4J The module provides a natchez EntryPoint that sends tracing information to an SLF4J logger. At OVO, we use this to make it easy to see tracing information in the terminal when running locally. Once you have the EntryPoint you can then use natchez as described in its README. Installation Add this module and an SLF4J binding (in this example we’re using Logback) to your build.sbt: val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"ch.qos.logback\" % \"logback-classic\" % \"1.2.3\", \"com.ovoenergy\" %% \"natchez-extras-slf4j\" % natchezExtrasVersion ) Example usage import cats.effect.{ExitCode, IO, IOApp} import com.ovoenergy.natchez.extras.slf4j.Slf4j import natchez.EntryPoint import scala.concurrent.duration._ object MyTracedApp extends IOApp { /** * Create a Natchez entrypoint that will send traces to SLF4J */ val slf4j: EntryPoint[IO] = Slf4j.entryPoint[IO] /** * This app creates a root span, adds a tag to set the env to UAT * then creates a subspan that sleeps for two seconds */ def run(args: List[String]): IO[ExitCode] = slf4j.root(\"root-span\").use { rootSpan =&gt; for { _ &lt;- rootSpan.put(\"env\" -&gt; \"uat\") _ &lt;- rootSpan.span(\"child-span\").use(_ =&gt; IO.sleep(2.seconds)) } yield ExitCode.Success } } If you run this example, you should see some output like this: sbt&gt; run [info] Running MyTracedApp 14:14:26.296 [ioapp-compute-0] INFO natchez - root-span started 14:14:26.305 [ioapp-compute-0] INFO natchez - child-span started 14:14:28.318 [ioapp-compute-1] INFO natchez - child-span success 14:14:28.320 [ioapp-compute-1] INFO natchez - root-span success [success] Total time: 3 s, completed 22-May-2020 14:14:28 Although you can see when each trace started and ended, eagle-eyed readers will notice that the env tag we added to the root span is not visible. This is because span tags get added to the SLF4J MDC (mapped diagnostic context) and by default Logback doesn’t print the MDC to the console. We can fix this by creating a Logback config file at src/main/resources/logback.xml with the following content (the important part is that the &lt;pattern&gt; element includes the %mdc pattern): {% raw %} &lt;?xml version=\"1.0\" encoding=\"UTF-8\" ?&gt; &lt;configuration&gt; &lt;appender name=\"CONSOLE\" class=\"ch.qos.logback.core.ConsoleAppender\"&gt; &lt;encoder&gt; &lt;pattern&gt;%d{HH:mm:ss.SSS} [%thread] %level %logger{36} - %msg {%mdc}%n&lt;/pattern&gt; &lt;/encoder&gt; &lt;/appender&gt; &lt;root level=\"debug\"&gt; &lt;appender-ref ref=\"CONSOLE\" /&gt; &lt;/root&gt; &lt;/configuration&gt; {% endraw %} Now if we run the code again, we can see the span tags in the console too: [info] Running MyTracedApp 14:31:19.835 [ioapp-compute-0] INFO natchez - root-span started {traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28} 14:31:19.846 [ioapp-compute-0] INFO natchez - child-span started {env=uat, traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28} 14:31:21.859 [ioapp-compute-1] INFO natchez - child-span success {env=uat, traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28} 14:31:21.860 [ioapp-compute-1] INFO natchez - root-span success {env=uat, traceToken=b54bfc5d-91e7-497e-9227-f2380da4ba28} [success] Total time: 3 s, completed 22-May-2020 14:31:21"
    } ,    
    {
      "title": "Natchez Testkit",
      "url": "/natchez-extras/docs/natchez-testkit.html",
      "content": "Natchez Testkit Natchez teskit is a small module that provides a TestEntryPoint backed by a Ref so you can write unit tests that check your application is sending the right information to Natchez. Installation val natchezExtrasVersion = \"4.0.1\" libraryDependencies ++= Seq( \"com.ovoenergy\" %% \"natchez-extras-testkit\" % natchezExtrasVersion % Test ) Usage An example of how to use it can be found in the test for natchez-extras-fs2: https://github.com/ovotech/effect-utils/blob/master/natchez-fs2/src/test/scala/com/ovoenergy/effect/natchez/AllocatedSpanTest.scala"
    } ,      
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
