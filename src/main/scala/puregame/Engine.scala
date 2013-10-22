package puregame

import scalaz._
import scalaz.effect.IO
import scalaz.effect.IO._
import scala.concurrent.ExecutionContext.Implicits.global
import Data._
import AtomicSTRef._
import scalaz.concurrent.Future

object Engine {

  import Scalaz._

  def handleAction(action: Action, delay: Int = 4000): (StateT[Id, GameBoard, _], Future[Unit]) =
    action match {
      case j: Join =>
        val state =
          for {
            board <- AtomicSTRef.get[GameBoard]
            newPlayer = Player(j.name, j.callback, 0, PlayerStats(0, 0, 0)) //in the State Monad, generate a new version of the world with the new player
            _ <- AtomicSTRef.update(board.copy(players = board.players.updated(j.name, newPlayer))) //replace the old version iwth the new version
          } yield ()
        (state, Future {})

      case m: Move =>
        val state = positionPlayerLens(m.name) := m.position //:= returns a state which fits in with our 
        (state, Future {})

      case bp: BombPlaced =>
        val state = markBomb(bp.position, true)
        (state, Future {
          Thread.sleep(delay) //bomb is ticking down...
          handleAction(BombExplodes(bp.name, bp.position))
        })

      case be: BombExplodes =>
        val state = handleBombExplodes(be.name, be.position)
        (state, Future {})
    }

  def incrementPlayerWounds(players: List[Player]) =
    Foldable[List].sequenceS_(players.map(p => woundStatsPlayerLens(p.name) := +1))

  def handleBombExplodes(p: String, position: Int): StateT[Id, GameBoard, Unit] =
    for {
      board <- AtomicSTRef.get[GameBoard]
      hitPlayers = getHitPlayers(position, board)
      _ <- incrementPlayerWounds(hitPlayers)
      _ <- hitStatsPlayerLens(p) := +hitPlayers.size
      _ <- markBomb(position, false)
    } yield ()

  def getHitPlayers(p: Int, board: GameBoard): List[Player] = {
    val blastArea = getBlastArea(p, 10)
    board.players
      .map(_._2)
      .filter(p => blastArea.contains(p.position))
      .toList

  }

  def getUntilEdge(pos: Int, size: Int, ctr: Int, chk: (Int, Int) => Int, f: (Int, Int) => Int): List[Int] =
    if (chk(pos, ctr) % size == 0)
      List[Int]()
    else
      List[Int](f(pos, ctr)) ::: getUntilEdge(pos, size, ctr + 1, chk, f)

  def getBlastArea(pos: Int, size: Int): List[Int] = {
    val right = getUntilEdge(pos, size, 0, (_ + _), (_ + _))
    val left = getUntilEdge(pos, size, 0, (_ - _), (_ - _))
    val up = getUntilEdge(pos, size, 0, (_ + _), (a, b) => a + (b * size))
    val down = getUntilEdge(pos, size, 0, (_ - _), (a, b) => a - (b * size))
    right ::: left ::: up ::: down
  }

}

