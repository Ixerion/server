package com.battleroyale.service

import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.battleroyale.model.Player
import fs2.concurrent.Queue
import org.http4s.websocket.WebSocketFrame

trait GameService[F[_]] {
  def createQueueForPlayer(player: Player): F[Queue[F, WebSocketFrame]]
}

object GameService {

  def of[F[_] : Monad : Concurrent](gameRef: Ref[F, Map[Player, Queue[F, WebSocketFrame]]]): GameService[F] = new GameService[F] {

    def createQueueForPlayer(player: Player): F[Queue[F, WebSocketFrame]] = {
      Queue.unbounded[F, WebSocketFrame].flatMap {
        queue =>
          for {
            playersAndQueues <- gameRef.updateAndGet(playerQueuesMap => playerQueuesMap + (player -> queue))
            _ = println(playersAndQueues)
          } yield queue
      }
    }
  }

}
