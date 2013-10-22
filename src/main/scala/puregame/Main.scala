package puregame

import scalaz._
import scalaz.effect.IO
import scalaz.effect.IO._
import scala.concurrent.ExecutionContext.Implicits.global
import Data._
import AtomicSTRef._
import scalaz.concurrent.Future
import Scalaz._
import scalaz.effect._
import argonaut._, Argonaut._
import javax.servlet.http._

object PureGameMain {
  import puregame.Data._
  import puregame.EncodeWorld._

  def main(args: Array[String]): Unit = {
    val world = AtomicSTRef(GameBoard(Map[String, Player](), Map[Int, Boolean]()))
    val io = for {
      s <- WebHelper.initServer(8080, incoming(world))
      _ = s.start()
      _ <- putStr("Started Server...")
    } yield ()

    io.unsafePerformIO

  }

  def getAction(req: HttpServletRequest): Action = ???

  def incoming(world: AtomicSTRef[GameBoard]) = (req: HttpServletRequest, outgoing: String => IO[Unit]) => {
    val action = getAction(req)
    val (s, f) = Engine.handleAction(action)
    val io = for {
      (newWorld, _) <- world.commit(s)
      _ = f.run //potential side effecting future
      _ <- outgoing(newWorld.asJson.toString)
    } yield ()
    io.unsafePerformIO
  }

}

