package com.battleroyale.model

import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

final case class Player(id: String)

object Player {

  implicit val playerDecoder: Decoder[Player] = deriveDecoder[Player]
}
