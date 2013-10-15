package puregame

import scalaz._
import Scalaz._
import scala.concurrent.Future
import scalaz.effect.IO
import scalaz.Lens

object Data {

  case class PlayerStats(bombsThrown: Int, wounds: Int, playersHit: Int)
  case class Player(name: String, callback: String, position: Int, stats: PlayerStats)
  case class BoardPiece(players: Map[String, Player], hasBomb: Boolean)
  case class GameBoard(players: Map[String, Player], bombs: Map[Int, Boolean])

  sealed trait Action

  case class Join(name: String, callback: String) extends Action
  case class Move(name: String, position: Int) extends Action
  case class BombPlaced(name: String, position: Int) extends Action //name is the user who placed it
  case class BombExplodes(name: String, position: Int) extends Action //name is the user who placed it

  //Player lens
  val positionLens = VLens.lensu[Player, Int]((player, np) => player.copy(position = np), _.position) //for those unfamiliar with scala, "_.position" is lambda shorthand for "player=>player.position"

  val statsLens = VLens.lensu[Player, PlayerStats]((player, ns) => player.copy(stats = ns), _.stats)

  //Stat field lenses
  val bombLens = VLens.lensu[PlayerStats, Int]((stats, thrown) => stats.copy(bombsThrown = thrown), _.bombsThrown)
  val woundLens = VLens.lensu[PlayerStats, Int]((stats, nw) => stats.copy(wounds = nw), _.wounds)
  val hitLens = VLens.lensu[PlayerStats, Int]((stats, nh) => stats.copy(playersHit = nh), _.playersHit)

  //stat field lenses composed with stat lense
  val woundStatsLens = statsLens.andThen(woundLens)
  val hitStatsLens = statsLens.andThen(hitLens)
  val bombStatsLens = statsLens.andThen(bombLens)

  //we can't create futher lenses without knowing which 'Player' to operate on, so instead we'll do it dynamically with a function call
  val playerLens = (k: String) => VLens.lensu[GameBoard, Player]((gb, pl) => gb.copy(players = gb.players.updated(k, pl)), _.players(k))
  val bombStatsPlayerLens = (k: String) => (playerLens(k).andThen(bombStatsLens))
  val woundStatsPlayerLens = (k: String) => (playerLens(k).andThen(woundStatsLens))
  val hitStatsPlayerLens = (k: String) => (playerLens(k).andThen(hitStatsLens))
  val positionPlayerLens = (k: String) => (playerLens(k).andThen(positionLens))

  val markBomb = (i: Int, newb: Boolean) => StateT[Id, GameBoard, Unit](b => (b.copy(bombs = b.bombs.updated(i, newb)), ()))
}

case class VLens[A, B](set: (A, B) => A, get: A => B) {
  def andThen[C](otherLens: VLens[B, C]): VLens[A, C] = {
    VLens[A, C]((a, c) => {
      set(a, otherLens.set(get(a), c))
    }, (a) => otherLens.get(get(a)))
  }

  def mods(b: B) = State[A, B](a => {
    (set(a, b), b)
  })

  def :=(b: B) = mods(b)
}

object VLens {
  def lensu[A, B](set: (A, B) => A, get: A => B) =
    VLens(set, get)
}

