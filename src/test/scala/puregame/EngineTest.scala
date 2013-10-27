package puregame

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.scalacheck.Prop._
import org.scalacheck._
import Gen._
import Arbitrary.arbitrary
import Data._

object ScalaCheckDemo extends Properties("puregameTest") {

  val gridSize = 100
  //val randomGridConstrait = Gen.choose(0,100)

  //generate ten random players

  //have them move around. 

  val playerGen = for {
    name <- arbitrary[Char]
    position <- Gen.choose(0, 100)
  } yield Player(name.toString(), "", position, PlayerStats(0, 0, 0))

  val players = Gen.containerOfN[List, Player](10, playerGen)

  val gameboardGen = for {
    players <- Gen.containerOfN[List, Player](10, playerGen)
    playerMap = players.map(p => (p.name, p)).toMap
  } yield GameBoard(playerMap, Map[Int, Boolean]())

  /************/

  property("bombExplodes") = forAll { (i: Int) =>
    (i >= 0 && i < 100) ==> {
      val res = for {
        gb <- gameboardGen.sample
        bombthrower <- oneOf(gb.players.map(kv => kv._1).toList).sample
        newWorld = Engine.handleBombExplodes(bombthrower, i).run(gb)._1
      } yield {
        val totalWounds = newWorld.players.map(_._2.stats.wounds).foldLeft(0)(_ + _)
        val hits = newWorld.players(bombthrower).stats.playersHit
        totalWounds == hits
      }

      res.get //yolo
    }
  }

  /*
property("startsWith") = forAll { (x: String, y: String) =>
    (x + y).startsWith(x)
  }
  */

}

