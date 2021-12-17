package com.battleroyale.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class Action(playerId: String, answer: Answer)

object Action {

  implicit val actionDecoder: Decoder[Action] = deriveDecoder[Action]
}
