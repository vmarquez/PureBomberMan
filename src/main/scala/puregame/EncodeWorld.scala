package puregame

import scalaz._
import Scalaz._
import argonaut._
import Argonaut._
import Data._

object EncodeWorld {
  /*
  case class PlayerStats(bombsThrown: Int, wounds: Int, playersHit: Int)
  case class Player(name: String, callback: String, position: Int, stats: PlayerStats)
  case class BoardPiece(players: Map[String, Player], hasBomb: Boolean)
  case class GameBoard(players: Map[String, Player], bombs: Map[Int, Boolean])
  */

  implicit def PlayerStatsWrapper: EncodeJson[PlayerStats] =
    EncodeJson((
      p: PlayerStats) =>
      ("bombsThrown" := p.bombsThrown) ->:
        ("wounds" := p.wounds) ->:
        ("playersHit" := p.playersHit) ->:
        jEmptyObject
    )

  implicit def PlayerWrapper: EncodeJson[Player] =
    EncodeJson((
      p: Player) =>
      ("name" := p.name) ->:
        ("callback" := p.name) ->:
        ("position" := p.position) ->:
        ("stats" := p.stats.asJson) ->:
        jEmptyObject
    )

  implicit def GameBoardWrapper: EncodeJson[GameBoard] =
    EncodeJson((
      gb: GameBoard) =>
      ("players" := jObjectAssocList(gb.players.map(t => (t._1 -> t._2.asJson)).toList)) ->:
        ("bombs" := jObjectAssocList(gb.bombs.map(t => (t._1.toString -> jBool(t._2))).toList)) ->:
        jEmptyObject
    )
}
