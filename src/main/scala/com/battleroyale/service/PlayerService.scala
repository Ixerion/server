package com.battleroyale.service

import cats.Monad
import cats.effect.concurrent.Ref
import cats.implicits._
import com.battleroyale.model.Player

import java.util.UUID

trait PlayerService[F[_]] {
  def createPlayer: F[Player]
  def removePlayer(playerId: String): F[Unit]
  def playersList: F[List[String]]
}

object PlayerService {

  def of[F[_] : Monad](ref: Ref[F, List[String]]): PlayerService[F] = new PlayerService[F] {
    def createPlayer: F[Player] = for {
      players <- ref.get
      freshPlayer = Player(UUID.randomUUID().toString)
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

