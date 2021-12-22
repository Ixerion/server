package com.battleroyale.model

import com.battleroyale.model.Player.PlayerId
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class Action(playerId: PlayerId, answer: Answer)

object Action {

  implicit val actionDecoder: Decoder[Action] = deriveDecoder[Action]
}
