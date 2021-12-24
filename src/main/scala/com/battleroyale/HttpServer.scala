package com.battleroyale

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.syntax.all._
import com.battleroyale.conf.{AppConfig, ServiceConf}
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.{GameState, Message}
import com.battleroyale.routes.WebSocketRoutes
import com.battleroyale.service.{GameService, PlayerService, QuestionService, QueueService}
import com.evolutiongaming.catshelper.LogOf
import fs2.concurrent.Queue
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext

object HttpServer {

  def run[F[_] : ConcurrentEffect : Timer]: F[ExitCode] = for {
    playerRef <- Ref.of[F, List[PlayerId]](List.empty)
    queueRef <- Ref.of[F, Map[PlayerId, Queue[F, Message]]](Map.empty)
    gameStateRef <- Ref.of[F, GameState](GameState(everyoneAnswered = false, Map.empty, None))
    implicit0(logOf: LogOf[F]) <- LogOf.slf4j[F]
    questionService <- QuestionService.of[F]
    playerService <- PlayerService.of[F](playerRef)
    queueService <- QueueService.of[F](queueRef)
    gameService <- GameService.of[F](playerService, queueService, gameStateRef, questionService)
    wsRoutes = WebSocketRoutes[F](queueService, playerService, gameService).routes
    appConf <- ConfigSource.default.load[ServiceConf] match {
      case Left(_)      => ConcurrentEffect[F].raiseError(new RuntimeException("Error loading config"))
      case Right(value) => ConcurrentEffect[F].pure(AppConfig(value))
    }
    finalApp = Logger.httpApp(logHeaders = true, logBody = true)(wsRoutes.orNotFound)
    _ <- BlazeServerBuilder[F](ExecutionContext.global)
      .bindHttp(port = appConf.serviceConf.port.number, host = appConf.serviceConf.host)
      .withHttpApp(finalApp)
      .serve
      .compile
      .drain
  } yield ExitCode.Success
}
