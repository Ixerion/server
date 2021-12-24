package com.battleroyale.service

import cats.effect.Sync
import cats.implicits._
import com.battleroyale.model.Player.PlayerId
import com.battleroyale.model.{GameState, Question}
import com.evolutiongaming.catshelper.LogOf

import scala.util.Random

trait QuestionService[F[_]] {

  def generateQuestion: F[Question]
  def findTheStupidOne(gameState: GameState): F[Option[PlayerId]]
}

object QuestionService {

  def of[F[_] : Sync : LogOf]: F[QuestionService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new QuestionService[F] {
        def generateQuestion: F[Question] = for {
          question <- Sync[F].delay(generate)
          _ <- log.info(s"New question created: ${question.description}, correct answer: ${question.correctAnswer}")
        } yield question

        private def generate: Question = {
          val operations = List("-", "+", "*")
          val random = new Random
          val stringRepresentation = s"${(math.random() * 10).toInt} ${operations(random.nextInt(operations.size))} ${(math.random() * 10).toInt}"
          val answer = stringRepresentation.split("\\s").toList match {
            case l :: "+" :: r :: _ => l.toInt + r.toInt
            case l :: "-" :: r :: _ => l.toInt - r.toInt
            case l :: "*" :: r :: _ => l.toInt * r.toInt
          }

          Question(stringRepresentation, answer)
        }

        def findTheStupidOne(gameState: GameState): F[Option[PlayerId]] = {
          for {
            correctAnswer <- gameState.question match {
              case Some(question) => Sync[F].pure(question.correctAnswer)
              case None           => Sync[F].raiseError(new RuntimeException("Unexpected Game State!"))
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
