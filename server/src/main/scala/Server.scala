package snackomaten 

class Server(val port: Int):
  val quit = Concurrent.ThreadSafe.MutableFlag()

  import scala.jdk.CollectionConverters.*
  val allConnections = java.util.concurrent.ConcurrentLinkedQueue[Network.Connection]()

  def timestamp: String = java.util.Date().toString

  def outputPrompt: String = s"[$timestamp]"

  lazy val serverPort = Network.openServerPort(port)

  def log(msg: String) = println(s"\n$outputPrompt $msg")

  def startMsg(): Unit = log(s"Server started, port: $port")

  def spawnAcceptLoop() = Concurrent.Run:
    while quit.isFalse do
      val connection = Network.Connection.toClient(from = serverPort)
      log(s"[INFO] New connection created: $connection")
      allConnections.add(connection)
      spawnReceiveLoop(connection)
      log(s"[INFO] Number of connections: ${allConnections.size()}")
    end while


  def spawnReceiveLoop(connection: Network.Connection) =  Concurrent.Run:
    while quit.isFalse do
      val msg = connection.read() 
      log(s"[Info] Received: '$msg'")
      connection.write(s"Server accepted: '$msg'")
      for c <- allConnections.asScala do
        Terminal.putGreen(s"Broadcasting on connection $c: '$msg'")
        c.write(s"Server broadcast $c: '$msg'")
    end while

  def start(): Unit = 
    log(s"[info] Server starting at: $serverPort")
    spawnAcceptLoop()
    while quit.isFalse do ()
