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
  import scalaz.syntax.FoldableSyntax

  def main(args: Array[String]): Unit = {
    val world = AtomicSTRef(GameBoard(Map[String, Player](), Map[Int, Boolean]()))
    val io = for {
      s <- WebHelper.initServer(8080, incoming(world))
      _ = s.start()
      _ <- putStr("Started Server...")
    } yield ()

    io.unsafePerformIO

  }

  def getAction(req: HttpServletRequest) =
    //val in = getClass.getResourceAsStream("rootPage")
    for {
      name <- Option(req.getParameter("name"))
      action <- Option(req.getParameter("action")) if (action == "join" || action == "move" || action == "bomb")
      posStr <- Option(req.getParameter("position"))
      position = Integer.parseInt(posStr)
    } yield action match {
      case "join" => Join(name, "")
      case "move" => Move(name, position)
      case "bomb" => BombPlaced(name, position)
    }

  def incoming(world: AtomicSTRef[GameBoard]) = (req: HttpServletRequest, outgoing: String => IO[Unit]) => {
    val io = for {
      action <- (OptionT(IO { getAction(req) })) //this isn't performing IO, but we want a transformer for readability
      (s1, f) = Engine.handleAction(action)
      (newWorld, _) <- world.commit(s1).liftM[OptionT]
      _ <- outgoing(newWorld.asJson.toString).liftM[OptionT]
      s2 = f.run //this is  potentially side effecting (blocking) 
      (nWorld2, _) <- OptionT(Traverse[Option].sequence(s2.map(s => world.commit(s))))
      _ <- outgoing(nWorld2.asJson.toString).liftM[OptionT] //send our update first//
    } yield ()
    io.run.unsafePerformIO //this is effectlively the end of the owrld for us.  It just happens on every incoming connection/request
    ()
  }
}

