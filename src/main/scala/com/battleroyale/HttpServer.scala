package com.battleroyale

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.syntax.all._
import com.battleroyale.model.GameState
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.Question.NoQuestion
import com.battleroyale.routes.WebSocketRoutes
import com.battleroyale.service.{GameService, QuestionService, PlayerService, QueueService}
import com.evolutiongaming.catshelper.LogOf
import fs2.concurrent.Queue
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.ExecutionContext

object HttpServer {

  def run[F[_] : ConcurrentEffect : Timer]: F[ExitCode] =
    for {
      playerRef <- Ref.of[F, List[PlayerId]](List.empty)
      queueRef <- Ref.of[F, Map[PlayerId, Queue[F, WebSocketFrame]]](Map.empty)
      gameStateRef <- Ref.of[F, GameState](GameState(everyoneAnswered = false, Map.empty, NoQuestion))
      implicit0(logOf: LogOf[F]) <- LogOf.slf4j[F]
      mathProblemService <- QuestionService.of[F]
      playerService <- PlayerService.of[F](playerRef)
      queueService <- QueueService.of[F](queueRef)
      gameService <- GameService.of[F](playerService, queueService, gameStateRef, mathProblemService)
      wsRoutes = WebSocketRoutes[F](queueService, playerService, gameService).routes

      finalApp = Logger.httpApp(logHeaders = true, logBody = true)(wsRoutes.orNotFound)
      _ <- BlazeServerBuilder[F](ExecutionContext.global)
        .bindHttp(port = 9001, host = "localhost")
        .withHttpApp(finalApp)
        .serve
        .compile
        .drain
    } yield ExitCode.Success
}
