package com.battleroyale.service

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.Player

import java.util.UUID

trait PlayerService[F[_]] {
  def createPlayer: F[Player]

  def playersList: F[List[Player]]
}

object PlayerService {

  def of[F[_] : Monad](ref: Ref[F, List[Player]]): PlayerService[F] = new PlayerService[F] {
    def createPlayer: F[Player] = for {
      players <- ref.get
      freshPlayer = Player(UUID.randomUUID().toString)
      updatedList = players :+ freshPlayer
      _ <- ref.update(_ => updatedList)
    } yield freshPlayer

    override def playersList: F[List[Player]] = for {
      players <- ref.get
    } yield players
  }
}

