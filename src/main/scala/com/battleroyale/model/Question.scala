package com.battleroyale.model

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

trait Question

object Question {

  final case class GeneratedQuestion(description: String, correctAnswer: Int) extends Question
  final case object NoQuestion extends Question

  implicit val questionEncoder: Encoder[GeneratedQuestion] = deriveEncoder[GeneratedQuestion]
}
