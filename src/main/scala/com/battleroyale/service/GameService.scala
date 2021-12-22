package com.battleroyale.service

import cats.Applicative
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.Question.NoQuestion
import com.battleroyale.model.{Action, Answer, GameState}
import com.evolutiongaming.catshelper.LogOf
import org.http4s.websocket.WebSocketFrame

trait GameService[F[_]] {

  def analyzeAnswer(action: Action): F[Unit]
  def initiateGameCycle(players: List[PlayerId]): F[Unit]
  def deletePlayer(playerId: PlayerId): F[Unit]
  def endGame(players: List[PlayerId]): F[Unit]
  def analyzeGameState(action: Action): F[GameState]
}

object GameService {
  def of[F[_] : Sync : LogOf](playerService: PlayerService[F], queueService: QueueService[F],
                               gameRef: Ref[F, GameState], mathProblemService: QuestionService[F]): F[GameService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new GameService[F] {

        def analyzeGameState(action: Action): F[GameState] = for {
          currentState <- gameRef.updateAndGet(w => w.copy(playersWithAnswers = w.playersWithAnswers.updated(action.playerId, action.answer)))
          playersWithNoAnswer = currentState.playersWithAnswers.filter(v => v._2.value == 0)
          everyoneAnswered = playersWithNoAnswer.isEmpty
          _ <- Applicative[F].whenA(everyoneAnswered)(analyzeAnswerAndKickPlayer(currentState))
          state <- gameRef.get
          _ <- queueService.createNotificationForAllPlayers(WebSocketFrame.Text(s"Current Game State: ${state.playersWithAnswers}"))
        } yield GameState(everyoneAnswered, state.playersWithAnswers, NoQuestion)

        def endGame(players: List[PlayerId]): F[Unit] = {
          val lastPlayer = players.head
          for {
            _ <- queueService.createNotificationForPlayer(lastPlayer, WebSocketFrame.Text("WINNER WINNER CHICKEN DINNER"))
          } yield ()
        }

        def analyzeAnswer(action: Action): F[Unit] = for {
          gameState <- analyzeGameState(action)
          _ <- gameState match {
            case GameState(everyoneAnswered, playersWithAnswers, NoQuestion) => if(playersWithAnswers.keys.toList.size == 1) {
              endGame(playersWithAnswers.keys.toList)
            } else if(everyoneAnswered) {
              initiateGameCycle(playersWithAnswers.keys.toList)
            } else {
              Sync[F].unit
            }
          }
        } yield ()

        def analyzeAnswerAndKickPlayer(gameState: GameState): F[Unit] = for {
          whoToKick <- mathProblemService.findTheStupidOne(gameState)
          _ <- deletePlayer(whoToKick)
        } yield ()

        def initiateGameCycle(players: List[PlayerId]): F[Unit] = {
          val playersInGame = players.map(playerId => (playerId, Answer(0))).toMap
          for {
            _ <- gameRef.update(_.copy(playersWithAnswers = playersInGame))
            generatedQuestion <- mathProblemService.generateMathProblem
            _ <- gameRef.updateAndGet(_.copy(question = generatedQuestion))
            _ <- queueService.createNotificationForAllPlayers(WebSocketFrame.Text(s"Initiating new game cycle... \nNew question: ${generatedQuestion.description}"))
          } yield ()
        }

        def deletePlayer(playerId: PlayerId): F[Unit] = for {
          _ <- queueService.createNotificationForPlayer(playerId, WebSocketFrame.Text("Sorry, you lost")) *>
            queueService.createNotificationForPlayer(playerId, WebSocketFrame.Close())
          _ <- queueService.deleteNotificationsForPlayer(playerId)
          _ <- playerService.removePlayer(playerId)
          stateBeforeDeletion <- gameRef.get
          updated <- gameRef.updateAndGet(_.copy(playersWithAnswers = stateBeforeDeletion.playersWithAnswers - playerId))
          _ <- queueService.createNotificationForAllPlayers(WebSocketFrame.Text(s"After successful deletion: ${updated.playersWithAnswers.toString()}"))
        } yield ()
      }
  }
}


