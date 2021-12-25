package com.battleroyale.conf

case class ServiceConf(host: String, port: Int)

case class AppConfig(serviceConf: ServiceConf, gameConf: GameConf)

case class GameConf(lobbySize: Int)
