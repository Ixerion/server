package com.battleroyale.routes

import cats.Applicative
import cats.effect.Concurrent
import cats.syntax.all._
import com.battleroyale.conf.GameConf
import com.battleroyale.model.{Action, Message, Player}
import com.battleroyale.service.{GameService, PlayerService, QueueService}
import fs2.{Pipe, Stream}
import io.circe.jawn._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

final case class WebSocketRoutes[F[_] : Concurrent](queueService: QueueService[F], playerService: PlayerService[F], gameService: GameService[F], gameConf: GameConf)
  extends Http4sDsl[F] {

  // websocat "ws://localhost:9001/client"
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "client" =>

      def receivePipe(player: Player): Pipe[F, WebSocketFrame, Unit] = (stream: Stream[F, WebSocketFrame]) => stream.evalMap {
        case WebSocketFrame.Text(message, _) =>
          Concurrent[F].delay(decode[Action](message)).flatMap {
            case Left(_)       => queueService.createNotificationForPlayer(player.id, Message("Wrong format, try again"))
            case Right(action) => gameService.analyzeAnswer(player.id, action)
          }

        case WebSocketFrame.Close(_) => Concurrent[F].unit
      }

      for {
        player <- playerService.createPlayer
        queue <- queueService.createQueueForPlayer(player.id)
        currentPlayers <- playerService.playersList
        _ <- queueService.createNotificationForPlayer(player.id, Message(s"Your id: ${player.id}")) *>
          queueService.createNotificationForAllPlayers(Message(s"Current players amount: ${currentPlayers.size}"))
        _ <- Applicative[F].whenA(currentPlayers.size == gameConf.lobbySize)(gameService.initiateGameCycle(currentPlayers))

        response <- WebSocketBuilder[F].build(
          send = queue.dequeue.map(message => WebSocketFrame.Text(message.description)),
          receive = receivePipe(player))
      } yield response
  }
}
