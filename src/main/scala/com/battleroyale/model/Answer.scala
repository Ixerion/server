package com.battleroyale.model

import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveUnwrappedCodec

final case class Answer(value: Int) extends AnyVal

object Answer {

  implicit val answerCodec: Codec[Answer] = deriveUnwrappedCodec[Answer]
}
