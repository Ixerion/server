package com.battleroyale.model

import com.battleroyale.model.Player.PlayerId

final case class GameState(everyoneAnswered: Boolean, playersWithAnswers: Map[PlayerId, Answer])
