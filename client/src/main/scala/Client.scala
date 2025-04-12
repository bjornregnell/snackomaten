package snackomaten 

class Client(val userId: String, masterPassword: String, val host: String = "bjornix.cs.lth.se", val port: Int = 8090):

  def retryBackoffMillis(): Int = 2000 + util.Random().nextInt(5000)
  val MaxRetryMillis = 100_000

  val isBuffering = Concurrent.MutableFlag(false)

  val quit = Concurrent.MutableFlag(false)

  val isShowStackTrace = false

  @volatile private var connectionOpt: Option[Network.Connection] = None

  val messageBuffer = Concurrent.MutableFifoSeq[Message]()

  def isActive = connectionOpt.isDefined && connectionOpt.get.isActive

  def closeConnection(): Unit = for c <- connectionOpt do 
    Terminal.putYellow(s"Closing ${if !isActive then "inactive " else ""}connection $c")
    c.close()
    connectionOpt = None

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
        Terminal.putGreen(s"Received from userId ${Console.YELLOW}${m.userId}${Console.GREEN} at ${m.time.toDate}:")
        Terminal.put(m.body)
      else 
        Terminal.putYellow(s"Received unknown message from Server:\n$m")

  def spawnReceiveLoop() = 
    Concurrent.Run:
      Terminal.putGreen(s"spawnReceiveLoop() started in new thread: ${Thread.currentThread()}")
      while quit.isFalse do
        awaitConnection()
        connectionOpt match 
          case None => ()
          case Some(connection) => 
            connection.awaitInput() match
            case Network.Failed(e) => 
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
                case Left(e) =>
                  Terminal.putRed(s"spawnReceiveLoop $e")
      end while
      Terminal.putGreen(s"spawnReceiveLoop() thread done: ${Thread.currentThread()}")

  def helpText = """
    Type some text followed by ENTER to send a message to other connected clients.
    Type just ENTER to toggle buffering mode.
    Type ? followed by ENTER for help.
    Ctrl+D to quit.
    Ctrl+A to move to beginning of line.
    Ctrl+E to move to end of line.
    Ctrl+K to kill letters until end of line.
    Ctrl+Y to yank killed letters.
    Arrow Up/Down to navigate history.
    !sometext to search for sometext in history
  """

  def commandLoop(): Unit = 
    var continue = true
    while continue do
      def bufferingState = if isBuffering.isFalse then "Buffering is OFF" else "Buffering is ON" 
      val info = s"$bufferingState; type message from $userId> "

      if isBuffering.isFalse then Terminal.putGreen(info) else Terminal.putRed(info)
      
      val input = Terminal.awaitInput()
      if input == Terminal.CtrlD then continue = false 
      else if input == "?" then Terminal.putYellow(helpText)
      else if input.isEmpty then 
        isBuffering.toggle() 
        if isBuffering.isFalse 
        then Terminal.putGreen(s"$bufferingState; incoming messages are printed even if you are typing.")
        else Terminal.alert(s"$bufferingState; all incoming messages are buffered in this terminal! Press enter to toggle buffering mode.")
        Terminal.putYellow(helpText)
        if isBuffering.isFalse then messageBuffer.foreach(showMessage)
      else 
        send(input)
    end while
    quit.setTrue()
    closeConnection()
    Terminal.putGreen(s"Goodbye $userId! snackomaten.Client terminates")

  def start(): Unit = 
    Terminal.putYellow(s"Connecting to snackomaten.Server host=$host port=$port")
    awaitConnection()
    Terminal.putYellow(helpText)
    Terminal.putGreen("You may want to start a another client in another terminal window with buffering mode toggled ON so you can type and send your messages without being interrupted by any incoming messages. Press ENTER to toggle buffering mode.")
    spawnReceiveLoop()
    commandLoop()
  
end Client