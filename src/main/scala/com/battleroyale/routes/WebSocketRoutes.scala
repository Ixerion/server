package com.battleroyale.routes

import cats.Applicative
import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import com.battleroyale.model.Action
import com.battleroyale.service.{GameService, PlayerService, QueueService}
import fs2.{Pipe, Stream}
import io.circe.jawn._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

final case class WebSocketRoutes[F[_] : Concurrent : Timer](queueService: QueueService[F], playerService: PlayerService[F], gameService: GameService[F])
  extends Http4sDsl[F] {

  // websocat "ws://localhost:9001/client"
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "client" =>

      val receivePipe: Pipe[F, WebSocketFrame, Unit] = (stream: Stream[F, WebSocketFrame]) => stream.evalMap {
        case WebSocketFrame.Text(message, _) =>
          Concurrent[F].delay(decode[Action](message)).flatMap {
            case Left(_)       => Concurrent[F].unit //TODO add error for specific user (add playerId param to receivePipe)
            case Right(action) => gameService.analyzeAnswer(action)
          }
      }

      for {
        player <- playerService.createPlayer
        queue <- queueService.createQueueForPlayer(player.id) //TODO switch queue type to [F, Message] or something like that
        currentPlayers <- playerService.playersList
        _ <- queueService.createNotificationForPlayer(player.id, WebSocketFrame.Text(s"Your id: ${player.id}")) *>
          queueService.createNotificationForPlayers(WebSocketFrame.Text(s"Current players amount: ${currentPlayers.size}"))
        _ <- Applicative[F].whenA(currentPlayers.size == 3)(
          queueService.createNotificationForPlayers(WebSocketFrame.Text("GAME STARTED")) *> gameService.initiateGameCycle)

        response <- WebSocketBuilder[F].build(
          send = queue.dequeue,
          receive = receivePipe)
      } yield response
  }
}
