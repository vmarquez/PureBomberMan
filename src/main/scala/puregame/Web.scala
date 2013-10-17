package puregame

import javax.servlet.http._
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketServlet
import org.eclipse.jetty.websocket.WebSocket.Connection
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage

class WorldWebSocketServlet extends WebSocketServlet {
  import puregame.AtomicSTMap._

  val userSocketMap = AtomicSTMap[String, WorldWebSocket]()

  protected override def doGet(request: HttpServletRequest, response: HttpServletResponse): Unit = {
  }

  def doWebSocketConnect(req: HttpServletRequest, arg1: String): WorldWebSocket = {
    val newsocket = new WorldWebSocket();
    userSocketMap.commit(add(req.getParameter("username"), newsocket))
    newsocket
  }

  def updateWorld(s: String): IO[Unit] =
    (for {
      map <- userSocketMap.frozen
    } yield map.foreach(kv => kv._2.send(s)))

}

class WorldWebSocket extends OnTextMessage {
  private var connection: Option[Connection] = None;

  def send(s: String) = connection.foreach(_.sendMessage(s))

  def onMessage(data: String) {
    //for now incoming data will be request/response... 
  }

  @Override
  def onOpen(conn: Connection): Unit = {
    this.connection = Some(conn)
  }

  @Override
  def onClose(closeCode: Int, message: String): Unit = {
  }
}
