package com.battleroyale.service

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.Player
import com.evolutiongaming.catshelper.LogOf

import java.util.UUID

trait PlayerService[F[_]] {
  def createPlayer: F[Player]
  def removePlayer(playerId: String): F[Unit]
  def playersList: F[List[String]]
}

object PlayerService {

  def of[F[_] : Monad : LogOf](ref: Ref[F, List[String]]): F[PlayerService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new PlayerService[F] {
        def createPlayer: F[Player] = for {
          players <- ref.get
          freshPlayer = Player(UUID.randomUUID().toString)
          _ <- log.info(s"Created player: ${freshPlayer.id}")
          updatedList = players :+ freshPlayer.id
          _ <- ref.update(_ => updatedList)
        } yield freshPlayer

        def playersList: F[List[String]] = for {
          players <- ref.get
        } yield players

        def removePlayer(playerId: String): F[Unit] = for {
          _ <- ref.update(list => list.filter(_ != playerId))
        } yield ()
      }
  }

}

