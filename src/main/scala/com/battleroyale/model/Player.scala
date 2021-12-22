package com.battleroyale.model

import com.battleroyale.model.Player.PlayerId
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class Player(id: PlayerId)

object Player {

  type PlayerId = String

  implicit val playerDecoder: Decoder[Player] = deriveDecoder[Player]
}
