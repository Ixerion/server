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

  def getThisStupidMap: F[Map[Player, Queue[F, WebSocketFrame]]]

  def createNotificationForPlayer(player: Player, webSocketFrame: WebSocketFrame): F[Unit]

  def createNotificationForPlayers(webSocketFrame: WebSocketFrame): F[Unit]
}

object GameService {

  def of[F[_] : Monad : Concurrent](gameRef: Ref[F, Map[Player, Queue[F, WebSocketFrame]]]): GameService[F] = new GameService[F] {

    def createQueueForPlayer(player: Player): F[Queue[F, WebSocketFrame]] = {
      Queue.unbounded[F, WebSocketFrame].flatMap {
        queue =>
          for {
            _ <- gameRef.updateAndGet(playerQueuesMap => playerQueuesMap + (player -> queue))
          } yield queue
      }
    }

    def getThisStupidMap: F[Map[Player, Queue[F, WebSocketFrame]]] = for {
      map <- gameRef.get
    } yield map

    def createNotificationForPlayer(player: Player, webSocketFrame: WebSocketFrame): F[Unit] = {
      val map = gameRef.get
      map.flatMap(playersMap => {
        val queue = playersMap(player)
        queue.enqueue1(webSocketFrame)
      })
    }

    override def createNotificationForPlayers(webSocketFrame: WebSocketFrame): F[Unit] = {
      val map = gameRef.get
      map.flatMap(playersMap => {
        playersMap.values.toList.map(_.enqueue1(webSocketFrame)).sequence_
      })
    }
  }
}
