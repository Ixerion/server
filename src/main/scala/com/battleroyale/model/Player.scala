package com.battleroyale.model

import com.battleroyale.model.Player.PlayerId

final case class Player(id: PlayerId)

object Player {
  type PlayerId = String
}
