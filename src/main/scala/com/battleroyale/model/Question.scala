package com.battleroyale.model

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

final case class Question(description: String, correctAnswer: Int)

object Question {

  implicit val questionEncoder: Encoder[Question] = deriveEncoder[Question]
}
