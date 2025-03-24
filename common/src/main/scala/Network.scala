package snackomaten 

object Network:
  import java.io.{DataInputStream, DataOutputStream}
  import java.io.{BufferedInputStream, BufferedOutputStream}
  import java.net.{Socket, ServerSocket}

  case class Error(error: Throwable)

  def streamsFromSocket(s: Socket): (DataInputStream, DataOutputStream) =
    DataInputStream(BufferedInputStream(s.getInputStream)) -> DataOutputStream(BufferedOutputStream(s.getOutputStream))

  def writeAndFlush(dos: DataOutputStream, msg: String): Unit = 
    dos.writeUTF(msg)
    dos.flush()
  
  case class ServerPort(serverSocket: ServerSocket):
    def port = serverSocket.getLocalPort
  
  def openServerPort(port: Int): ServerPort = ServerPort(ServerSocket(port))

  class Connection(val sock: Socket, val dis: DataInputStream, val dos: DataOutputStream):
    def awaitInput(): String | Error = try dis.readUTF catch case e: Throwable => Error(e) 
    def port: Int = sock.getPort()
    def isActive: Boolean = sock.isBound && !sock.isClosed && sock.isConnected && !sock.isInputShutdown && !sock.isOutputShutdown
    def write(msg: String): Unit | Error = writeAndFlush(dos, msg)
    def close(): Unit = 
      try if sock != null then sock.close catch case e: Throwable => ()
      try if dis  != null then dis.close  catch case e: Throwable => ()
      try if dos  != null then dos.close  catch case e: Throwable => ()

    override def toString: String = s"Connection($sock)"
  
  object Connection:
    def fromSocket(socket: Socket): Connection = 
      val (dis, dos) = streamsFromSocket(socket)
      Connection(socket, dis, dos)

    def awaitConnectClient(from: ServerPort): Either[Throwable, Connection] = 
      try
        val sock = from.serverSocket.accept()  // blocks until connection is made
        sock.setKeepAlive(true)
        Right(fromSocket(sock))
      catch case e: Throwable => Left(e)


    def connectToServer(host: String, port: Int): Either[Throwable, Connection] = 
      try 
        val sock = Socket(host, port)
        sock.setKeepAlive(true)
        Right(fromSocket(sock))
      catch case e: Throwable => Left(e)
  
  end Connection

end Network