package com.battleroyale.service

import cats.Monad
import cats.effect.Sync
import cats.implicits._
import com.battleroyale.model.GameState
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.Question.GeneratedQuestion
import com.evolutiongaming.catshelper.LogOf


trait QuestionService[F[_]] {

  def generateMathProblem: F[GeneratedQuestion]
  def findTheStupidOne(gameState: GameState): F[PlayerId]
}

object QuestionService {

  def of[F[_] : Sync : LogOf]: F[QuestionService[F]] = LogOf[F].apply(getClass).map {
    log => new QuestionService[F] {
      override def generateMathProblem: F[GeneratedQuestion] = for {
        question <- Monad[F].pure(GeneratedQuestion("3 + 2", 5))
        _ <- log.info(s"New question created: ${question.description}")
      } yield question

      override def findTheStupidOne(gameState: GameState): F[PlayerId] = for {
        playerToKick <- Sync[F].delay(gameState.playersWithAnswers.keys.toList.head)
        _ <- log.info(s"Player to kick: $playerToKick")
      } yield playerToKick
    }
  }
}
