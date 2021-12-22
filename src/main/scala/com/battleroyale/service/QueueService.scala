package com.battleroyale.service

import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.evolutiongaming.catshelper.LogOf
import fs2.concurrent.Queue
import org.http4s.websocket.WebSocketFrame

trait QueueService[F[_]] {
  def createQueueForPlayer(playerId: String): F[Queue[F, WebSocketFrame]]
  def createNotificationForPlayer(playerId: String, webSocketFrame: WebSocketFrame): F[Unit]
  def createNotificationForAllPlayers(webSocketFrame: WebSocketFrame): F[Unit]
  def createNotificationForSpecificPlayers(players: List[String], webSocketFrame: WebSocketFrame): F[Unit]
  def deleteNotificationsForPlayer(player: String): F[Unit]
}

object QueueService {

  def of[F[_] : Monad : Concurrent : LogOf](queueRef: Ref[F, Map[String, Queue[F, WebSocketFrame]]]): F[QueueService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new QueueService[F] {

        def createQueueForPlayer(playerId: String): F[Queue[F, WebSocketFrame]] = {
          Queue.unbounded[F, WebSocketFrame].flatMap {
            queue =>
              for {
                _ <- queueRef.update(playerQueueMap => playerQueueMap + (playerId -> queue))
              } yield queue
          }
        }

        def createNotificationForPlayer(playerId: String, webSocketFrame: WebSocketFrame): F[Unit] = {
          val map = queueRef.get
          map.flatMap(playersMap => {
            val queue = playersMap(playerId)
            queue.enqueue1(webSocketFrame)
          })
        }

        def createNotificationForAllPlayers(webSocketFrame: WebSocketFrame): F[Unit] = {
          val map = queueRef.get
          map.flatMap(playerQueueMap => {
            playerQueueMap.values.toList.map(_.enqueue1(webSocketFrame)).sequence_
          })
        }

        def createNotificationForSpecificPlayers(players: List[String], webSocketFrame: WebSocketFrame): F[Unit] = {
          val map = queueRef.get
          map.flatMap(playerQueueMap => {
            playerQueueMap.filter(v => players.contains(v._1)).values.toList.map(_.enqueue1(webSocketFrame)).sequence_
          })
        }

        def deleteNotificationsForPlayer(player: String): F[Unit] = for {
          _ <- queueRef.update(_ - player)
        } yield ()
      }
  }
}
