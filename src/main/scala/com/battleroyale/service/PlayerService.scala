package com.battleroyale.service

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.Player
import com.battleroyale.model.Player.PlayerId
import com.evolutiongaming.catshelper.LogOf

import java.util.UUID

trait PlayerService[F[_]] {
  def createPlayer: F[Player]
  def removePlayer(playerId: PlayerId): F[Unit]
  def playersList: F[List[PlayerId]]
}

object PlayerService {

  def of[F[_] : Monad : LogOf](ref: Ref[F, List[PlayerId]]): F[PlayerService[F]] = LogOf[F].apply(getClass).map {
    log =>
      new PlayerService[F] {

        def createPlayer: F[Player] =  {
          val player = Player(UUID.randomUUID().toString)
          ref.update(list => {
            list :+ player.id
          }).flatMap(_ => log.info(s"Created player: ${player.id}"))
            .map(_ => player)
        }

        def playersList: F[List[PlayerId]] = ref.get

        def removePlayer(playerId: PlayerId): F[Unit] = {
          ref.update(list => list.filter(_ != playerId))
        }
      }
  }

}

