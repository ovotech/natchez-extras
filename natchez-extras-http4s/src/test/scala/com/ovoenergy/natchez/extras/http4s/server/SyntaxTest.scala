package com.ovoenergy.natchez.extras.http4s.server

import cats.effect.IO
import com.ovoenergy.natchez.extras.http4s.server.syntax._
import org.http4s.Status.{InsufficientStorage, Ok}
import org.http4s.{HttpApp, HttpRoutes, Request, Response}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SyntaxTest extends AnyWordSpec with Matchers {

  "fallThroughTo" should {

    "Call the second Kleisli if the first returns None" in {
      val routes: HttpRoutes[IO] = HttpRoutes.empty
      val app: HttpApp[IO] = HttpApp.pure(Response(status = Ok))
      val result = routes.fallthroughTo(app).run(Request()).unsafeRunSync()
      result.status shouldBe Ok
    }

    "Not call the second Kleisli if the first returns a result" in {
      val routes: HttpRoutes[IO] = HttpRoutes.pure(Response(status = Ok))
      val app: HttpApp[IO] = HttpApp.pure(Response(status = InsufficientStorage))
      val result = routes.fallthroughTo(app).run(Request()).unsafeRunSync()
      result.status shouldBe Ok
    }
  }
}
