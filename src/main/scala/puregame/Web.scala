package puregame

import javax.servlet.http._
import org.eclipse.jetty.websocket.WebSocket
import org.eclipse.jetty.websocket.WebSocketServlet
import org.eclipse.jetty.websocket.WebSocket.Connection
import org.eclipse.jetty.websocket.WebSocket.OnTextMessage
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import scalaz.effect._

object WebHelper {
  import puregame.Data._

  def initServer(port: Int, incoming: (HttpServletRequest, String => IO[Unit]) => Unit): IO[Server] = {
    val ws = new Server(port)
    val context = new ServletContextHandler(ServletContextHandler.SESSIONS)
    context.setContextPath("/")
    ws.setHandler(context)
    val servlet = new PuregameWebSocketServlet(incoming)
    context.addServlet(new ServletHolder(servlet), "/*")
    IO { ws }
  }

}

class PuregameWebSocketServlet(handleIncoming: (HttpServletRequest, String => IO[Unit]) => Unit) extends WebSocketServlet {
  import puregame.AtomicSTMap._

  val userSocketMap = AtomicSTMap[String, PuregameWebSocket]()

  protected override def doGet(req: HttpServletRequest, response: HttpServletResponse): Unit = {
    handleIncoming(req, updateWorld)
  }

  def doWebSocketConnect(req: HttpServletRequest, arg1: String): PuregameWebSocket = {
    val newsocket = new PuregameWebSocket();
    userSocketMap.commit(add(req.getParameter("username"), newsocket))
    newsocket
  }

  def updateWorld(s: String): IO[Unit] =
    (for {
      map <- userSocketMap.frozen
    } yield map.foreach(kv => kv._2.send(s)))

}

class PuregameWebSocket extends OnTextMessage {
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
