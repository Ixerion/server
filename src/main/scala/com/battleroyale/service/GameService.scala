package com.battleroyale.service

import cats.effect.concurrent.Ref
import cats.implicits._
import cats.{Applicative, Monad}
import com.battleroyale.model.{Action, Answer}
import com.evolutiongaming.catshelper.LogOf
import org.http4s.websocket.WebSocketFrame

trait GameService[F[_]] {

  def analyzeAnswer(action: Action): F[Unit]
  def initiateGameCycle: F[Unit]
  def deletePlayer(playerId: String): F[Unit]
}

object GameService {
  def of[F[_] : Monad : LogOf](playerService: PlayerService[F], queueService: QueueService[F],
                               gameRef: Ref[F, Map[String, Answer]]): F[GameService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new GameService[F] {

        def analyzeAnswer(action: Action): F[Unit] = for {
          //TODO gameState resolver?
          gameState <- gameRef.updateAndGet(_.updated(action.playerId, action.answer))
          filteredMap = gameState.filter(v => v._2.value == 0)
          playersWithNoAnswer = filteredMap.keys.toList
          _ <- queueService.createNotificationForSpecificPlayers(playersWithNoAnswer, WebSocketFrame.Text("Please answer something")) *>
            queueService.createNotificationForPlayers(WebSocketFrame.Text(gameState.toString()))
          _ <- Applicative[F].whenA(playersWithNoAnswer.size == 1)(deletePlayer(playersWithNoAnswer.head))
          //TODO delete user with wrong answer here
        } yield ()

        def initiateGameCycle: F[Unit] = for {
          players <- playerService.playersList
          initiated = players.map(playerId => (playerId, Answer(0))).toMap
          _ <- gameRef.update(_ => initiated)
          _ <- queueService.createNotificationForPlayers(WebSocketFrame.Text(initiated.toString()))
          //TODO send math problem here
        } yield ()

        def deletePlayer(playerId: String): F[Unit] = for {
          init <- gameRef.get
          _ <- playerService.removePlayer(playerId)
          updated = init - playerId
          _ <- queueService.createNotificationForPlayer(playerId, WebSocketFrame.Text("Sorry, you lost"))
          _ <- gameRef.update(_ => updated)
          _ <- queueService.createNotificationForPlayers(WebSocketFrame.Text(updated.toString()))
        } yield ()
      }
  }
}


