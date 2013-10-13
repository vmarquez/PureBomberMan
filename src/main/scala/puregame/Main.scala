package puregame

import scalaz._
import scalaz.effect.IO
import scalaz.effect.IO._
import scala.concurrent.ExecutionContext.Implicits.global
import Data._
import AtomicSTRef._
import scalaz.concurrent.Future
import Scalaz._

object PureGameMain {
  import Scalaz._
  def main(args: Array[String]): Unit = {
    //todo: start up jetty/handle push JSON 
  }

  def incoming(action: Action, world: AtomicSTRef[GameBoard], outgoing: GameBoard => Unit): Unit = {
    val (s, f) = Engine.handleAction(action)
    val io =
      for {
        (newWorld, _) <- world.commit(s)
        _ = outgoing(newWorld)
        _ = f.run //potential side effecting future
      } yield ()

    io.unsafePerformIO

  }

}

trait Server {
  def send(s: String): IO[Unit]
  def incoming(f: String => Unit): IO[Unit]
}

