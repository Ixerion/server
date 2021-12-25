package com.battleroyale

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.syntax.all._
import com.battleroyale.conf.{AppConfig, ServiceConf}
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.{GameState, Message}
import com.battleroyale.routes.GameRoutes
import com.battleroyale.service.{GameService, PlayerService, QuestionService, QueueService}
import com.evolutiongaming.catshelper.LogOf
import fs2.concurrent.Queue
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext

case class Services[F[_]](queueService: QueueService[F], playerService: PlayerService[F])

object HttpServer {

  private def initServices[F[_]: ConcurrentEffect : Timer : LogOf](playerRef: Ref[F, List[PlayerId]], queueRef: Ref[F, Map[PlayerId, Queue[F, Message]]]): F[Services[F]] = for {
    queueService <- QueueService.of[F](queueRef)
    playerService <- PlayerService.of[F](playerRef)
  } yield Services(queueService, playerService)

  def run[F[_] : ConcurrentEffect : Timer]: F[ExitCode] = for {
    appConf <- ConfigSource.default.load[AppConfig] match {
      case Left(error)  => ConcurrentEffect[F].raiseError(new RuntimeException(s"$error"))
      case Right(value) => ConcurrentEffect[F].pure(value)
    }
    playerRef <- Ref.of[F, List[PlayerId]](List.empty)
    queueRef <- Ref.of[F, Map[PlayerId, Queue[F, Message]]](Map.empty)
    gameStateRef <- Ref.of[F, GameState](GameState(everyoneAnswered = false, Map.empty, None))
    implicit0(logOf: LogOf[F]) <- LogOf.slf4j[F]
    questionService <- QuestionService.of[F]
    services <- initServices[F](playerRef, queueRef)
    gameService <- GameService.of[F](services.playerService, services.queueService, gameStateRef, questionService)
    wsRoutes = GameRoutes[F](services.queueService, services.playerService, gameService, appConf.gameConf).routes

    finalApp = Logger.httpApp(logHeaders = true, logBody = true)(wsRoutes.orNotFound)
    _ <- BlazeServerBuilder[F](ExecutionContext.global)
      .bindHttp(port = appConf.serviceConf.port, host = appConf.serviceConf.host)
      .withHttpApp(finalApp)
      .serve
      .compile
      .drain
  } yield ExitCode.Success
}
