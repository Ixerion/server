package com.battleroyale.service

import cats.effect.IO
import cats.effect.concurrent.Ref
import com.battleroyale.model.Player
import com.battleroyale.model.Player.PlayerId
import com.evolutiongaming.catshelper.LogOf
import com.evolutiongaming.catshelper.testkit.PureTest
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PlayerServiceSpec extends AnyWordSpec with MockFactory with Matchers {

  val playerRef: Ref[IO, List[PlayerId]] = mock[Ref[IO, List[PlayerId]]]

  "player service spec" must {
    "create players" in PureTest[IO].of {
      env =>
        import env._
        implicit val logOf: LogOf[IO] = LogOf.empty[IO]
        (playerRef.get _).expects().returning(IO.pure(List()))
        (playerRef.update _).expects(*).returning(IO.unit)

        for {
          service <- PlayerService.of[IO](playerRef)
          _ <- service.createPlayer.map(_.isInstanceOf[Player] mustBe true)
        } yield ()
    }
  }
}
