package snackomaten 

class Client(val userId: String, val host: String = "bjornix.cs.lth.se", val port: Int = 8090):

  val retryReconnectAfterMillis = 5000

  val isWatching = Concurrent.ThreadSafe.MutableFlag(true)

  val quit = Concurrent.ThreadSafe.MutableFlag(false)

  val isShowStackTrace = false

  @volatile private var connectionOpt: Option[Network.Connection] = None

  val messageBuffer = Concurrent.ThreadSafe.MutableFirstInFirstOutQueue[Message]()

  def isActive = connectionOpt.isDefined && connectionOpt.get.isActive

  def closeConnection(): Unit = for c <- connectionOpt do 
    Terminal.putRed(s"Closing ${if !isActive then "inactive " else ""}connection $c")
    try c.close() catch case e: Throwable => Terminal.showError(e, showStackTrace = isShowStackTrace)
    connectionOpt = None
    Terminal.putRed("No connection.")

  final def retryConnectIfNoActiveConnection(): Unit = synchronized:
    var continue = false
    while 
      continue = false
      if !isActive then closeConnection()
      if !connectionOpt.isDefined then
        try 
          connectionOpt = Some(Network.Connection.fromSocket(java.net.Socket(host, port)))
        catch case e: Throwable => 
          Terminal.showError(e, showStackTrace = isShowStackTrace)
          Terminal.alert("Server seems currently unavailable...")
          connectionOpt = None
          Terminal.putYellow(s"Attempting to reconnect after ${(retryReconnectAfterMillis/1000.0).round} s ...")
          Thread.sleep(retryReconnectAfterMillis)
          continue = true
      continue
    do ()


  def send(msg: String): Unit = 
    retryConnectIfNoActiveConnection()
    Terminal.putGreen(s"Sending '$msg' via $connectionOpt: isActive=$isActive")
    try connectionOpt.get.write(s"userId=$userId;msg=$msg")
    catch case e: Throwable => 
      Terminal.showError(e, showStackTrace = isShowStackTrace)
      closeConnection()
      send(msg)

  @annotation.tailrec
  final def receive(): String =
    retryConnectIfNoActiveConnection()
    try connectionOpt.get.read()
    catch case e: Throwable => 
      Terminal.putYellow(e.getMessage()) //showError(e, showStackTrace = isShowStackTrace)
      for c <- connectionOpt do try c.close() catch case e: Throwable => Terminal.putYellow(e.getMessage())
      connectionOpt = None
      if quit.isFalse then receive() else "Quitting..."

  def showMessage(m: Message): Unit = 
    if m.text.nonEmpty then
      val userId = m.get("userId")
      if userId.isEmpty then Terminal.putYellow(m.text) else
        Terminal.putGreen(s"From ${userId.get}:")
        m.get("msg").foreach(Terminal.put)

  def spawnReceiveLoop() = 
    Concurrent.Run:
      Terminal.putGreen(s"spawnReceiveLoop() started in new thread: ${Thread.currentThread()}")
      while quit.isFalse do
        val msg = Message(receive())
        if isWatching.isTrue then
          messageBuffer.removeAllToSeq().foreach(showMessage)
          showMessage(msg)
        else
          messageBuffer.add(msg)
      end while
      Terminal.putGreen(s"spawnReceiveLoop() thread done: ${Thread.currentThread()}")

  def helpText = """
    Type some text followed by ENTER to send a message to other connected clients.
    Type just ENTER to toggle watch mode.
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
      def watchOnOff = if isWatching.isTrue then "Watch mode is ON" else "Watch mode is OFF" 
      val info = s"$watchOnOff; type message from $userId> "

      if isWatching.isTrue then Terminal.putBlue(info) else Terminal.putRed(info)
      
      val cmd = Terminal.get()
      if cmd == Terminal.CtrlD then continue = false 
      else if cmd == "?" then Terminal.putYellow(helpText)
      else if cmd.isEmpty then 
        isWatching.toggle() 
        if isWatching.isTrue 
        then Terminal.putGreen(s"$watchOnOff; incoming messages are printed even if you are typing.")
        else Terminal.alert(s"$watchOnOff; all incoming messages are buffered in this terminal! Press enter to toggle watch mode.")
        Terminal.putYellow(helpText)
        if isWatching.isTrue then messageBuffer.removeAllToSeq().foreach(showMessage)
      else 
        send(cmd)
    end while
    quit.setTrue()
    for c <- connectionOpt do c.close()
    Terminal.putGreen(s"Goodbye $userId! snackomaten.Client terminates")

  def start(): Unit = 
    Terminal.putYellow(s"Connecting to snackomaten.Server host=$host port=$port")
    retryConnectIfNoActiveConnection()
    Terminal.putGreen("Connected!")
    Terminal.putYellow(helpText)
    Terminal.putGreen("You may want to start a another client in another terminal window with watch mode toggled off so you can type and send your messages without being interrupted by any incoming messages. Press ENTER to toggle watch mode.")
    spawnReceiveLoop()
    commandLoop()
  
end Client