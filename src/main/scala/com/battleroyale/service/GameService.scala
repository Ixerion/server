package com.battleroyale.service

import cats.Applicative
import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.{Action, GameState, Message}
import com.evolutiongaming.catshelper.LogOf

trait GameService[F[_]] {

  def analyzeAnswer(player: PlayerId, action: Action): F[Unit]
  def initiateGameCycle(players: List[PlayerId]): F[Unit]
  def deletePlayer(playerId: PlayerId): F[Unit]
  def endGame(players: List[PlayerId]): F[Unit]
  def updateGameState(playerId: PlayerId, action: Action): F[Either[String, GameState]]
  def kickPlayerWithWrongAnswer(gameState: GameState): F[Unit]
  def analyzeCorrectPlayerAnswer(playerId: PlayerId, action: Action): F[GameState]
}

object GameService {
  def of[F[_] : Sync : LogOf](playerService: PlayerService[F], queueService: QueueService[F],
                              gameStateRef: Ref[F, GameState], questionService: QuestionService[F]): F[GameService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new GameService[F] {

        def updateGameState(playerId: PlayerId, action: Action): F[Either[String, GameState]] = for {
          currentState <- gameStateRef.get
          updated <- if (currentState.playersWithAnswers.contains(playerId)) {
            analyzeCorrectPlayerAnswer(playerId, action).map(Right(_))
          } else {
            Sync[F].pure(Left("Sorry, you are not in the game"))
          }
          _ <- log.info(s"Game state: ${updated.toString}")
        } yield updated

        def endGame(players: List[PlayerId]): F[Unit] = {
          val lastPlayer = players.head
          for {
            _ <- queueService.createNotificationForPlayer(lastPlayer, Message("WINNER WINNER CHICKEN DINNER"))
          } yield ()
        }

        def analyzeCorrectPlayerAnswer(playerId: PlayerId, action: Action): F[GameState] = for {
          currentState <- gameStateRef.updateAndGet(w => w.copy(playersWithAnswers = w.playersWithAnswers.updated(playerId, action.answer.some)))
          _ <- queueService.createNotificationForPlayer(playerId, Message("Answer accepted"))
          playersWithNoAnswer = !currentState.playersWithAnswers.exists { case (_, maybeAnswer) => maybeAnswer.isEmpty }
          _ <- Applicative[F].whenA(playersWithNoAnswer)(kickPlayerWithWrongAnswer(currentState))
          state <- gameStateRef.get
        } yield state.copy(everyoneAnswered = playersWithNoAnswer)

        def analyzeAnswer(playerId: PlayerId, action: Action): F[Unit] = for {
          gameState <- updateGameState(playerId, action)
          _ <- gameState match {
            case Left(message)                                             => queueService.createNotificationForPlayer(playerId, Message(message)) *>
              queueService.deleteNotificationsForPlayer(playerId) *> playerService.removePlayer(playerId)
            case Right(GameState(everyoneAnswered, playersWithAnswers, _)) => if (playersWithAnswers.keys.toList.size == 1) {
              endGame(playersWithAnswers.keys.toList)
            } else if (everyoneAnswered) {
              initiateGameCycle(playersWithAnswers.keys.toList)
            } else {
              Sync[F].unit
            }
          }
        } yield ()

        def kickPlayerWithWrongAnswer(gameState: GameState): F[Unit] = for {
          whoToKick <- questionService.findTheStupidOne(gameState)
          _ <- whoToKick match {
            case Some(playerId) => deletePlayer(playerId)
            case None           => queueService.createNotificationForAllPlayers(Message("No players were deleted, generating new question..."))
          }
        } yield ()

        def initiateGameCycle(players: List[PlayerId]): F[Unit] = {
          val playersInGame = players.map(playerId => (playerId, None)).toMap
          for {
            _ <- gameStateRef.update(_.copy(playersWithAnswers = playersInGame))
            _ <- log.info(s"Players in game: $playersInGame")
            _ <- queueService.createNotificationForAllPlayers(Message(s"Players in game: $playersInGame"))
            generatedQuestion <- questionService.generateQuestion
            _ <- gameStateRef.updateAndGet(_.copy(question = Some(generatedQuestion)))
            _ <- queueService.createNotificationForAllPlayers(Message(s"Initiating new game cycle... \nNew question: ${generatedQuestion.description}"))
          } yield ()
        }

        def deletePlayer(playerId: PlayerId): F[Unit] = for {
          _ <- queueService.createNotificationForPlayer(playerId, Message("Sorry, you lost"))
          _ <- queueService.deleteNotificationsForPlayer(playerId)
          _ <- playerService.removePlayer(playerId)
          stateBeforeDeletion <- gameStateRef.get
          _ <- gameStateRef.update(_.copy(playersWithAnswers = stateBeforeDeletion.playersWithAnswers - playerId))
        } yield ()
      }
  }
}


