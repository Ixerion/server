package com.battleroyale

import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, ExitCode, Timer}
import cats.syntax.all._
import com.battleroyale.model.Player
import com.battleroyale.routes.WebSocketRoutes
import com.battleroyale.service.{GameService, PlayerService}
import fs2.concurrent.Queue
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.ExecutionContext

object HttpServer {

  def run[F[_] : ConcurrentEffect : Timer]: F[ExitCode] =
    for {
      playerRef <- Ref.of[F, List[Player]](List.empty)
      gameRef <- Ref.of[F, Map[Player, Queue[F, WebSocketFrame]]](Map.empty)
      playerService: PlayerService[F] = PlayerService.of[F](playerRef)
      gameService: GameService[F] = GameService.of[F](gameRef)
      wsRoutes = WebSocketRoutes[F](gameService, playerService).routes

      httpApp = wsRoutes.orNotFound
      finalApp = Logger.httpApp(logHeaders = true, logBody = true)(httpApp)
      _ <- BlazeServerBuilder[F](ExecutionContext.global)
        .bindHttp(port = 9001, host = "localhost")
        .withHttpApp(finalApp)
        .serve
        .compile
        .drain
    } yield ExitCode.Success
}
