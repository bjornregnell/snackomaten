package snackomaten 

class TerminalClient(
  val config: Config, 
  val userId: String, 
  val masterPassword: String, 
  val host: String = "bjornix.cs.lth.se", 
  val port: Int = 8090
):
  /** Gives a random number of millis between from and from + span. */
  def retryBackoffMillis(from: Int = 2000, span: Int = 5000): Int = 
    from + util.Random().nextInt(span)

  val MaxRetryMillis = 100_000

  val isBuffering = Concurrent.MutableFlag(false)

  val quit = Concurrent.MutableFlag(false)

  val isShowStackTrace = false

  @volatile private var connectionOpt: Option[Network.Connection] = None

  val messageBuffer = Concurrent.MutableFifoSeq[Message]()

  /** Check if the connection to the server is alive. */
  def isActive: Boolean = connectionOpt.isDefined && connectionOpt.get.isActive

  /** Close connection to the server if alive or else do nothing. */
  def closeConnection(): Unit = for c <- connectionOpt do 
    Terminal.putYellow(s"Closing ${if !isActive then "inactive " else ""}connection $c")
    c.close()
    connectionOpt = None

  /** Await connection to server. The waiting time before next retry is increased by `retryBackoffMillis()` */
  final def awaitConnection(): Unit = 
    synchronized:  // to prevent concurrent connection attempts from other threads
      var retryReconnectAfterMillis = retryBackoffMillis()
      var continue = false
      while 
        continue = false
        if !isActive then closeConnection()
        if connectionOpt.isEmpty then
          connectionOpt = 
            Network.Connection.connectToServer(host, port) match
              case c@Network.Connection(sock)  => 
                Terminal.putGreen(s"Connected via $sock!")
                Some(c) 
              case Network.Failed(error) => 
                Terminal.showError(error, showStackTrace = isShowStackTrace)
                Terminal.alert("  Server seems currently unavailable :(  ")
                connectionOpt = None
                val s = (retryReconnectAfterMillis / 1000.0).round
                Terminal.putYellow(s"Attempting to reconnect after $s seconds ... Press Ctrl+D to quit.")
                Thread.sleep(retryReconnectAfterMillis)
                if retryReconnectAfterMillis < MaxRetryMillis then retryReconnectAfterMillis += retryBackoffMillis()
                continue = true  // retry connection
                None
            end match
        end if
        continue
      do ()
  end awaitConnection

  def send(msg: String): Unit = 
    val m = Message(userId = userId, cmd = Message.Cmd.Send, body = msg).encode()
    Terminal.putGreen(s"Sending '$msg' via $connectionOpt: isActive=$isActive")
    try connectionOpt.get.write(m) 
    catch case e: Throwable => 
      Terminal.putRed(s"Error during send(): $e")
      closeConnection()
      Terminal.alert(s"Message lost: $msg")  
      Terminal.putYellow("Try again later...")

  def showMessage(m: Message): Unit = 
      if m.cmd == Message.Cmd.Send then 
        Terminal.putGreen(s"From ${Console.YELLOW}${Message.AtUserTag}${m.userId}${Console.GREEN} ${m.time.toDate}:")
        Terminal.put(m.body)
      else 
        Terminal.putYellow(s"Received unknown message from Server:\n$m")

  /** A new thread that awaits incoming messages from the server and either show them directly or buffers them. */
  def spawnReceiveLoop() = 
    Concurrent.Run:
      Terminal.putGreen(s"spawnReceiveLoop() started in new thread: ${Thread.currentThread()}")
      while quit.isFalse do
        awaitConnection()
        connectionOpt match 
          case None => ()
          case Some(connection) => 
            connection.awaitInput() match
            case Network.Failed(e) => e match
              case se: java.net.SocketException if se.getMessage() == "Socket closed" => 
                Terminal.putYellow("spawnReceiveLoop Socket closed")
                closeConnection()
              case _ => 
                Terminal.putYellow(s"spawnReceiveLoop Network.Error: $e")
                closeConnection()
            case input: String =>
              Message.decode(input) match
                case Right(m) => 
                  if isBuffering.isFalse then
                    messageBuffer.outAll().foreach(showMessage)
                    showMessage(m)
                  else
                    messageBuffer.put(m) 
                case Left(e) => Terminal.putRed(s"spawnReceiveLoop $e")
      end while
      Terminal.putGreen(s"spawnReceiveLoop() thread done: ${Thread.currentThread()}")

  def helpText = """
    Type some text followed by ENTER to send a message to other connected clients.
    Type just ENTER to toggle buffering mode.
    Type /help followed by ENTER to see this help text again.
    Ctrl+D to quit.
    Ctrl+A to move to beginning of line.
    Ctrl+E to move to end of line.
    Ctrl+K to kill letters until end of line.
    Ctrl+Y to yank killed letters.
    Arrow Up/Down to navigate history.
    !sometext to search for sometext in history
  """
  
  /** Awaits terminal input from user and acts on commands or sends messages. */
  def commandLoop(): Unit = 
    Thread.sleep(500) // to wait a bit to allow receive loop sto start
    var continue = true
    while continue do
      def bufferingState = if isBuffering.isFalse then "buf=OFF" else s"buf=ON, ${messageBuffer.size} in buf" 
      val info = s"$bufferingState. Type /help or message from $userId> "

      //if isBuffering.isFalse then Terminal.putGreen(info) else Terminal.putCyan(info)
      Terminal.putCyan(info)

      val input = Terminal.awaitInput()
      if input == Terminal.CtrlD then continue = false 
      else if input == "/help" then Terminal.putYellow(helpText)
      else if input.isEmpty then 
        isBuffering.toggle() 
        if isBuffering.isFalse then 
          Terminal.putGreen(s"$bufferingState; incoming messages are interleaved with your typing.")
        else
          Terminal.alert(s"Incoming messages are buffered!")
          Terminal.putYellow("Press enter again to toggle buffering OFF.")
        end if
        if isBuffering.isFalse && messageBuffer.size > 0 then 
          Terminal.alert(s"Draining ${messageBuffer.size} messages from buffer:")
          messageBuffer.outAll().foreach(showMessage)
      else 
        send(input)
    end while
    quit.setTrue()
    closeConnection()
    Terminal.putGreen(s"Goodbye $userId! snackomaten.Client terminates")
  
  /** Starts this client. Awaits connection to server before `spawnReceiveLoop()` and `commandLoop()`. */
  def start(): Unit = 
    Terminal.putYellow(s"Connecting to snackomaten.Server host=$host port=$port")
    awaitConnection()
    Terminal.putYellow(helpText)
    Terminal.putGreen("Press ENTER to toggle buffering mode.\nYou may want to start a another client in another terminal window with buffering mode toggled ON so you can type and send your messages without being interrupted by any incoming messages.")
    spawnReceiveLoop()
    commandLoop()
  
end TerminalClient