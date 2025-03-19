package snackomaten 

object Network:
  import java.io.{DataInputStream, DataOutputStream}
  import java.io.{BufferedInputStream, BufferedOutputStream}
  import java.net.{Socket, ServerSocket}

  def streamsFromSocket(s: Socket): (DataInputStream, DataOutputStream) =
    DataInputStream(BufferedInputStream(s.getInputStream)) ->
      DataOutputStream(BufferedOutputStream(s.getOutputStream))

  def writeAndFlush(dos: DataOutputStream, msg: String): Unit = 
    dos.writeUTF(msg)
    dos.flush()
  
  case class ServerPort(serverSocket: ServerSocket):
    def port = serverSocket.getLocalPort
  
  def openServerPort(port: Int): ServerPort = ServerPort(ServerSocket(port))

  class Connection(val sock: Socket, val dis: DataInputStream, val dos: DataOutputStream):
    def read(): String = dis.readUTF
    def port: Int = sock.getPort()
    def isActive: Boolean = sock.isBound && !sock.isClosed && sock.isConnected && !sock.isInputShutdown && !sock.isOutputShutdown
    def write(msg: String): Unit = writeAndFlush(dos, msg)
    def close(): Unit = { sock.close; dis.close; dos.close }
    override def toString: String = s"Connection($sock)"
  
  object Connection:
    def fromSocket(socket: Socket): Connection = 
      val (dis, dos) = streamsFromSocket(socket)
      Connection(socket, dis, dos)

    def toClient(from: ServerPort): Connection = 
      val sock = from.serverSocket.accept()
      sock.setKeepAlive(true)
      fromSocket(sock)

    def toServer(host: String, port: Int): Connection = 
      val sock = Socket(host, port)
      sock.setKeepAlive(true)
      fromSocket(sock)

end Network