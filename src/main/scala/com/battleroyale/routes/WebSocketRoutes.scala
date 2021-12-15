package com.battleroyale.routes

import cats.effect.Concurrent
import cats.syntax.all._
import com.battleroyale.service.{GameService, PlayerService}
import fs2._
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame

final case class WebSocketRoutes[F[_] : Concurrent](gameService: GameService[F], playerService: PlayerService[F])
  extends Http4sDsl[F] {

  // websocat "ws://localhost:9001/client"
  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "client" =>

      val echoPipe: Pipe[F, WebSocketFrame, WebSocketFrame] =
        _.collect {
          case WebSocketFrame.Text(message, _) => WebSocketFrame.Text(message)
        }

      for {
        player <- playerService.createPlayer
        _ = println(player)
        queue <- gameService.createQueueForPlayer(player)
        _ = println(queue)
        _ <- queue.enqueue1(WebSocketFrame.Text(s"your id: ${player.id}"))
        response <- WebSocketBuilder[F].build(
          // Outgoing stream of WebSocket messages to send to the client. (toClient)
          send = queue.dequeue.through(echoPipe),

          // Sink, where the incoming WebSocket messages from the client are pushed to. (fromClient)
          receive = queue.enqueue
        )
      } yield response
  }
}
