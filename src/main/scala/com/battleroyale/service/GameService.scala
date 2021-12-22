package com.battleroyale.service

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.{Action, Answer, GameState}
import com.evolutiongaming.catshelper.LogOf
import org.http4s.websocket.WebSocketFrame

trait GameService[F[_]] {

  def analyzeAnswer(action: Action): F[Unit]
  def initiateGameCycle(players: List[String]): F[Unit]
  def deletePlayer(playerId: String): F[Unit]
}

object GameService {
  def of[F[_] : Sync : LogOf](playerService: PlayerService[F], queueService: QueueService[F],
                               gameRef: Ref[F, Map[String, Answer]]): F[GameService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new GameService[F] {

        def analyzeGameState(action: Action): F[GameState] = for {
          currentState <- gameRef.updateAndGet(_.updated(action.playerId, action.answer))
          playersWithNoAnswer = currentState.filter(v => v._2.value == 0)
          everyoneAnswered = playersWithNoAnswer.isEmpty
          //TODO question resolver here
          _ <- if (everyoneAnswered) {
            deletePlayer(currentState.keys.toList.head)
          } else {
            Sync[F].unit
          }
          state <- gameRef.get
          _ <- queueService.createNotificationForAllPlayers(WebSocketFrame.Text(s"Current Game State: $state"))
        } yield GameState(everyoneAnswered, state)

        def endGame(players: List[String]): F[Unit] = {
          val lastPlayer = players.head
          for {
            _ <- queueService.createNotificationForPlayer(lastPlayer, WebSocketFrame.Text("WINNER WINNER CHICKEN DINNER"))
          } yield ()
        }

        def analyzeAnswer(action: Action): F[Unit] = for {
          gameState <- analyzeGameState(action)
          _ <- gameState match {
            case GameState(everyoneAnswered, playersWithAnswers) => if(playersWithAnswers.keys.toList.size == 1) {
              endGame(playersWithAnswers.keys.toList)
            } else if(everyoneAnswered) {
              initiateGameCycle(playersWithAnswers.keys.toList)
            } else {
              Sync[F].unit
            }
          }
        } yield ()

        def initiateGameCycle(players: List[String]): F[Unit] = {
          val playersInGame = players.map(playerId => (playerId, Answer(0))).toMap
          for {
            _ <- gameRef.update(_ => playersInGame)
            _ <- queueService.createNotificationForAllPlayers(WebSocketFrame.Text(s"Initiating new game cycle: ${playersInGame.toString()}"))
          } yield ()
        }/*for {
          //players <- playerService.playersList
          initiated <- players.map(playerId => (playerId, Answer(0))).toMap
          _ <- gameRef.update(_ => initiated)
          _ <- queueService.createNotificationForPlayers(WebSocketFrame.Text(initiated.toString()))
          //TODO send math problem here
        } yield ()*/

        def deletePlayer(playerId: String): F[Unit] = for {
          _ <- queueService.createNotificationForPlayer(playerId, WebSocketFrame.Text("Sorry, you lost"))
          _ <- queueService.deleteNotificationsForPlayer(playerId)
          _ <- playerService.removePlayer(playerId)
          updated <- gameRef.updateAndGet(_ - playerId)
          _ <- queueService.createNotificationForAllPlayers(WebSocketFrame.Text(updated.toString()))
        } yield ()
      }
  }
}


