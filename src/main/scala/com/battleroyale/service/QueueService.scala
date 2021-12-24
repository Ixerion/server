package com.battleroyale.service

import cats.effect.Concurrent
import cats.effect.concurrent.Ref
import cats.syntax.all._
import com.battleroyale.model.Message
import com.battleroyale.model.Player.PlayerId
import com.evolutiongaming.catshelper.LogOf
import fs2.concurrent.Queue

trait QueueService[F[_]] {
  def createQueueForPlayer(playerId: PlayerId): F[Queue[F, Message]]
  def createNotificationForPlayer(playerId: PlayerId, message: Message): F[Unit]
  def createNotificationForAllPlayers(message: Message): F[Unit]
  def createNotificationForSpecificPlayers(players: List[PlayerId], message: Message): F[Unit]
  def deleteNotificationsForPlayer(player: PlayerId): F[Unit]
}

object QueueService {

  def of[F[_] : Concurrent : LogOf](queueRef: Ref[F, Map[PlayerId, Queue[F, Message]]]): F[QueueService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new QueueService[F] {

        def createQueueForPlayer(playerId: PlayerId): F[Queue[F, Message]] = {
          Queue.unbounded[F, Message].flatMap {
            queue =>
              for {
                _ <- queueRef.update(playerQueueMap => playerQueueMap + (playerId -> queue))
              } yield queue
          }
        }

        def createNotificationForPlayer(playerId: PlayerId, message: Message): F[Unit] = {
          val map = queueRef.get
          map.flatMap(playersMap => {
            val queue = playersMap(playerId)
            queue.enqueue1(message)
          })
        }

        def createNotificationForAllPlayers(message: Message): F[Unit] = {
          val map = queueRef.get
          map.flatMap(playerQueueMap => {
            playerQueueMap.values.toList.map(_.enqueue1(message)).sequence_
          })
        }

        def createNotificationForSpecificPlayers(players: List[PlayerId], message: Message): F[Unit] = {
          val map = queueRef.get
          map.flatMap(playerQueueMap => {
            playerQueueMap.filter(v => players.contains(v._1)).values.toList.map(_.enqueue1(message)).sequence_
          })
        }

        def deleteNotificationsForPlayer(player: PlayerId): F[Unit] = for {
          _ <- queueRef.update(_ - player)
        } yield ()
      }
  }
}
