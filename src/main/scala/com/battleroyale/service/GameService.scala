package com.battleroyale.service

import cats.{Applicative, Monad}
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.{Action, Answer}
import org.http4s.websocket.WebSocketFrame

trait GameService[F[_]] {

  def analyzeAnswer(action: Action): F[Unit]
  def initiateGame: F[Unit]
  def deletePlayer(playerId: String): F[Unit]
  //TODO add game cycle or something
}

object GameService {
  def of[F[_] : Monad](playerService: PlayerService[F], queueService: QueueService[F],
               gameRef: Ref[F, Map[String, Answer]]): GameService[F] = new GameService[F] {

    def analyzeAnswer(action: Action): F[Unit] = for {
      gameState <- gameRef.updateAndGet(_.updated(action.playerId, action.answer))
      filteredMap = gameState.filter(v => v._2.value == 0)
      playersWithNoAnswer = filteredMap.keys.toList
      _ <- queueService.createNotificationForPlayers(playersWithNoAnswer, WebSocketFrame.Text("Please answer something")) *>
        queueService.createNotificationForPlayers(WebSocketFrame.Text(gameState.toString()))
      _ <- Applicative[F].whenA(playersWithNoAnswer.size == 1)(deletePlayer(playersWithNoAnswer.head))
    } yield ()

    def initiateGame: F[Unit] = for {
      players <- playerService.playersList
      initiated = players.map(playerId => (playerId, Answer(0))).toMap
      _ <- gameRef.update(_ => initiated)
      _ <- queueService.createNotificationForPlayers(WebSocketFrame.Text(initiated.toString()))
    } yield ()

    def deletePlayer(playerId: String): F[Unit] = for {
      init <- gameRef.get
      _ <- playerService.removePlayer(playerId)
      updated = init - playerId
      _ <- gameRef.update(_ => updated)
      _ <- queueService.createNotificationForPlayers(WebSocketFrame.Text(updated.toString()))
    } yield ()
  }
}


