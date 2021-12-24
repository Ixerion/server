package com.battleroyale.service

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.{GameState, Question}
import com.evolutiongaming.catshelper.LogOf

trait QuestionService[F[_]] {

  def generateMathProblem: F[Question]
  def findTheStupidOne(gameState: GameState): F[Option[PlayerId]]
}

object QuestionService {

  def of[F[_] : Sync : LogOf]: F[QuestionService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new QuestionService[F] {
        def generateMathProblem: F[Question] = for {
          question <- Monad[F].pure(Question("3 + 2", 5))
          _ <- log.info(s"New question created: ${question.description}")
        } yield question

        def findTheStupidOne(gameState: GameState): F[Option[PlayerId]] = {
          for {
            correctAnswer <- gameState.question match {
              case Some(question) => Sync[F].pure(question.correctAnswer)
              case None        => Sync[F].raiseError(new RuntimeException("Unexpected Game State!"))
            }
            playerToKick <- Sync[F].delay({
              val players = gameState.playersWithAnswers
              if (players.values.collect { case Some(value) => value }.toSet.size == 1)
                None
              else {
                players
                  .collect { case (playerId, Some(value)) => (playerId, value) }
                  .maxBy { case (_, answer) => math.abs(correctAnswer - answer.value) }._1.some
              }
            })
            _ <- log.info(s"Player to kick: $playerToKick")
          } yield playerToKick
        }
      }
  }
}