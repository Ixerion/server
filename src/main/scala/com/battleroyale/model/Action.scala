package com.battleroyale.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class Action(answer: Answer)

object Action {

  implicit val actionDecoder: Decoder[Action] = deriveDecoder[Action]
}
