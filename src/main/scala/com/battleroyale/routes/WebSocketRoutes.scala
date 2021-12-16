package com.battleroyale.routes

import cats.Applicative
import cats.effect.{Concurrent, Timer}
import cats.syntax.all._
import com.battleroyale.service.{GameService, PlayerService}
import fs2._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

final case class WebSocketRoutes[F[_] : Concurrent : Timer](gameService: GameService[F], playerService: PlayerService[F])
  extends Http4sDsl[F] {

  // websocat "ws://localhost:9001/client"
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "client" =>

      val echoPipe: Pipe[F, WebSocketFrame, WebSocketFrame] =
        _.collect {
          case WebSocketFrame.Text(message, _) => WebSocketFrame.Text(message)
        }

      val receivePipe: Pipe[F, WebSocketFrame, Unit] = (q: Stream[F, WebSocketFrame]) => q.evalMap {
        case WebSocketFrame.Text(message, _) => Concurrent[F].pure(println(s"received new message: $message"))
      }

      for {
        player <- playerService.createPlayer
        queue <- gameService.createQueueForPlayer(player)
        currentPlayers <- playerService.playersList
        _ <- gameService.createNotificationForPlayer(player, WebSocketFrame.Text(s"Your id: ${player.id}")) *>
          gameService.createNotificationForPlayers(WebSocketFrame.Text(s"Current players amount: ${currentPlayers.size}"))
        _ <- Applicative[F].whenA(currentPlayers.size == 3)(
          gameService.createNotificationForPlayers(WebSocketFrame.Text("GAME STARTED"))) //TODO start the game here

        /*gameServiceMap <- gameService.getThisStupidMap
        _ = println(gameServiceMap)
        list = gameServiceMap.values
        _ <- list.map(_.enqueue1(WebSocketFrame.Text(s"current players: ${currentPlayers.map(_.id).mkString(", ")}"))).toList.sequence*/

        /*response <- WebSocketBuilder[F].build(
          // Outgoing stream of WebSocket messages to send to the client. (toClient)
          send = queue.dequeue.through(echoPipe),

          // Sink, where the incoming WebSocket messages from the client are pushed to. (fromClient)
          receive = queue.enqueue
        )*/
        response <- WebSocketBuilder[F].build(
          send = queue.dequeue /*.merge(Stream.repeatEval(Concurrent[F].pure(WebSocketFrame.Text("2222222"))))*/
          /*.map(q => WebSocketFrame.Text(s"${}"))*/ ,
          receive = receivePipe)
      } yield response
  }
}
