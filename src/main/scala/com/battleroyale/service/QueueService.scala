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

        def createQueueForPlayer(playerId: PlayerId): F[Queue[F, Message]] = for {
          queue <- Queue.unbounded[F, Message]
          _ <- queueRef.update(playerQueueMap => playerQueueMap + (playerId -> queue))
          _ <- log.info(s"Created new Queue for player $playerId")
        } yield queue

        def createNotificationForPlayer(playerId: PlayerId, message: Message): F[Unit] = for {
          playersMap <- queueRef.get
          queue = playersMap.get(playerId)
          _ <- queue match {
            case Some(queue) => queue.enqueue1(message)
            case None        => Concurrent[F].unit
          }
        } yield ()

        def createNotificationForAllPlayers(message: Message): F[Unit] = for {
          playersMap <- queueRef.get
          _ <- playersMap.values.toList.map(_.enqueue1(message)).sequence_
        } yield ()

        def createNotificationForSpecificPlayers(players: List[PlayerId], message: Message): F[Unit] = for {
          playersMap <- queueRef.get
          _ <- playersMap.filter(pair => players.contains(pair._1)).values.toList.map(_.enqueue1(message)).sequence_
        } yield ()

        def deleteNotificationsForPlayer(player: PlayerId): F[Unit] = for {
          _ <- queueRef.update(_ - player)
        } yield ()
      }
  }
}
