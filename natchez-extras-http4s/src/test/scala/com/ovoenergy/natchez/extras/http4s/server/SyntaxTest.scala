package com.ovoenergy.natchez.extras.http4s.server

import cats.effect.IO
import com.ovoenergy.natchez.extras.http4s.server.syntax._
import munit.CatsEffectSuite
import org.http4s.Status.{InsufficientStorage, Ok}
import org.http4s.{HttpApp, HttpRoutes, Request, Response}

class SyntaxTest extends CatsEffectSuite {

  test("Call the second Kleisli if the first returns None") {
    val routes: HttpRoutes[IO] = HttpRoutes.empty
    val app: HttpApp[IO] = HttpApp.pure(Response(status = Ok))
    assertIO(routes.fallthroughTo(app).run(Request()).map(_.status), Ok)
  }

  test("Not call the second Kleisli if the first returns a result") {
    val routes: HttpRoutes[IO] = HttpRoutes.pure(Response(status = Ok))
    val app: HttpApp[IO] = HttpApp.pure(Response(status = InsufficientStorage))
    assertIO(routes.fallthroughTo(app).run(Request()).map(_.status), Ok)
  }
}
