package com.battleroyale.model

final case class GameState(question: Question, state: Boolean, playersWithAnswers: Map[Player, Answer])
