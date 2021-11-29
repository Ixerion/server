package com.battleroyale

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder

import scala.concurrent.ExecutionContext

object HttpServer extends IOApp {

  private val helloRoutes = HttpRoutes.of[IO] {

    // curl "localhost:9001/hello/world"
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name!")
  }

  private val httpApp = Seq(
    helloRoutes
  ).reduce(_ <+> _).orNotFound

  override def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO](ExecutionContext.global)
      .bindHttp(port = 9001, host = "localhost")
      .withHttpApp(httpApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
