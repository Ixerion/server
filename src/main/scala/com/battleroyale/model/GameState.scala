package com.battleroyale.model

final case class GameState(everyoneAnswered: Boolean, playersWithAnswers: Map[String, Answer])
