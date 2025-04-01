package snackomaten 

object Network:
  import java.io.{DataInputStream, DataOutputStream}
  import java.io.{BufferedInputStream, BufferedOutputStream}
  import java.net.{Socket, ServerSocket}

  case class Failed(error: Throwable)

  def writeAndFlush(dos: DataOutputStream, msg: String): Unit = 
    dos.writeUTF(msg)
    dos.flush()
  
  case class ServerPort(serverSocket: ServerSocket):
    def port = serverSocket.getLocalPort
  
  def openServerPort(port: Int): ServerPort = ServerPort(ServerSocket(port))

  case class Connection(val sock: Socket):
    val (dis, dos) = Connection.streamsFromSocket(sock)
    def awaitInput(): String | Failed = try dis.readUTF catch case e: Throwable => Failed(e) 
    def port: Int = sock.getPort()
    def isActive: Boolean = sock.isBound && !sock.isClosed && sock.isConnected && !sock.isInputShutdown && !sock.isOutputShutdown
    def write(msg: String): Unit | Failed = writeAndFlush(dos, msg)
    def close(): Unit = 
      try if sock != null then sock.close catch case e: Throwable => ()
      try if dis  != null then dis.close  catch case e: Throwable => ()
      try if dos  != null then dos.close  catch case e: Throwable => ()

    override def toString: String = s"Connection($sock)"
  
  object Connection:
    def streamsFromSocket(s: Socket): (DataInputStream, DataOutputStream) =
      DataInputStream(BufferedInputStream(s.getInputStream)) -> DataOutputStream(BufferedOutputStream(s.getOutputStream))

    def awaitConnectClient(from: ServerPort): Connection | Failed = 
      try
        val sock = from.serverSocket.accept()  // blocks until connection is made
        sock.setKeepAlive(true)
        Connection(sock)
      catch case e: Throwable => Failed(e)


    def connectToServer(host: String, port: Int): Connection | Failed = 
      try 
        val sock = Socket(host, port)
        sock.setKeepAlive(true)
        Connection(sock)
      catch case e: Throwable => Failed(e)
  
  end Connection

end Network