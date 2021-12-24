package com.battleroyale.conf

case class Port(number: Int) extends AnyVal

case class ServiceConf(host: String, port: Port)

case class AppConfig(serviceConf: ServiceConf)
