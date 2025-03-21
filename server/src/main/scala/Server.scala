package snackomaten 

class Server(val port: Int):
  val quit = Concurrent.ThreadSafe.MutableFlag()

  import scala.jdk.CollectionConverters.*
  val allConnections = java.util.concurrent.ConcurrentLinkedQueue[Network.Connection]()

  def timestamp: String = java.util.Date().toString

  def outputPrompt: String = s"[$timestamp]"

  lazy val serverPort = Network.openServerPort(port)

  def log(msg: String) = Terminal.put(s"$outputPrompt $msg")

  def startMsg(): Unit = log(s"Server started, port: $port")

  def spawnAcceptLoop() = Concurrent.Run:
    try
      while quit.isFalse do
        val connection = Network.Connection.toClient(from = serverPort)
        log(s"New connection created: $connection")
        allConnections.add(connection)
        spawnReceiveLoop(connection)
        log(s"Number of connections: ${allConnections.size()}")
      end while
    catch case e: Throwable =>
      Terminal.showError(e)
      Terminal.alert("FATAL ERROR. Accept Loop terminated.")
      quit.setTrue()


  def spawnReceiveLoop(connection: Network.Connection) =  Concurrent.Run:
    try
      while quit.isFalse do
        val msg = connection.read() 
        log(s"Received: '$msg'")
        connection.write(s"[SERVER INFO] Snackomaten got your precious message.")
        for c <- allConnections.asScala if c != connection do
          Terminal.putGreen(s"Broadcasting to other $c:") 
          Terminal.putYellow(msg)
          c.write(msg)
      end while
    catch case e: Throwable =>
      Terminal.showError(e)
      Terminal.putRed(s"Connection lost: $connection \nReceive Loop terminated.")
      allConnections.remove(connection)
      log(s"Number of connections: ${allConnections.size()}")

  def start(): Unit = 
    log(s"Server starting at: $serverPort")
    spawnAcceptLoop()
    while quit.isFalse do ()
    Terminal.put("Server terminates. Goodbye!")
