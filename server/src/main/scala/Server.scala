package spamvas 

class Server(val port: Int):
  import scala.concurrent.ExecutionContext.Implicits._
  import scala.concurrent.Future
  import scala.util.{Try , Success, Failure}

  import scala.jdk.CollectionConverters.*
  val allConnections = java.util.concurrent.ConcurrentLinkedQueue[Network.Connection]()

  def timestamp: String = java.util.Date().toString

  def outputPrompt: String = s"[$timestamp]"

  lazy val serverPort = Network.openServerPort(port)

  def log(msg: String) = println(s"\n$outputPrompt $msg")

  def startMsg(): Unit = log(s"Server started, port: $port")

  def spawnAcceptLoop() = Future:
    while (true) {
      val connection = Network.Connection.toClient(from = serverPort)
      log(s"[INFO] New connection created: $connection")
      allConnections.add(connection)
      spawnReceiveLoop(connection)
      log(s"[INFO] Number of connections: ${allConnections.size()}")
    }


  def spawnReceiveLoop(connection: Network.Connection) =  
    Future {
      while (true) {
        val msg = connection.read
        log(s"[Info] Got spam '$msg'")
        connection.write(s"Echo spam: '$msg'")
        for c <- allConnections.asScala do
          c.write(s"Everyone spammed: $msg")
      }
    }

  def start(): Unit = 
    log(s"[info] Server starting at: $serverPort")
    spawnAcceptLoop()
    while true do ()
