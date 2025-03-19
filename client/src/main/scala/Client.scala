package snackomaten 

class Client(val userId: String, val host: String = "bjornix.cs.lth.se", val port: Int = 8090):

  val retryReconnectAfterMillis = 5000

  val isListening = Concurrent.ThreadSafe.MutableFlag(false)

  val isShowStackTrace = false

  @volatile private var connectionOpt: Option[Network.Connection] = None


  def isActive = connectionOpt.isDefined && connectionOpt.get.isActive

  def closeConnection(): Unit = for c <- connectionOpt do 
    Terminal.putRed(s"Attempting to close ${if !isActive then "inactive " else ""}connection $c")
    try c.close() catch case e: Throwable => Terminal.showError(e, showStackTrace = isShowStackTrace)
    connectionOpt = None
    Terminal.putRed("No connection.")

  @annotation.tailrec
  final def retryConnectIfNoActiveConnection(): Unit = 
    if !isActive then closeConnection()
    if !connectionOpt.isDefined then
      try 
        connectionOpt = Some(Network.Connection.fromSocket(java.net.Socket(host, port)))
      catch 
        case e: Throwable => 
          Terminal.showError(e, showStackTrace = isShowStackTrace)
          Terminal.alert("Server seems currently unavailable...")
          connectionOpt = None
          Terminal.putYellow(s"Attempting to reconnect after ${(retryReconnectAfterMillis/1000.0).round} s ...")
          Thread.sleep(retryReconnectAfterMillis)
          retryConnectIfNoActiveConnection()


  def send(msg: String): Unit = 
    retryConnectIfNoActiveConnection()
    Terminal.putGreen(s"Attempting to send '$msg' via $connectionOpt: isActive=$isActive")
    try connectionOpt.get.write(s"userId=$userId;msg=$msg")
    catch case e: Throwable => 
      Terminal.showError(e, showStackTrace = isShowStackTrace)
      closeConnection()
      send(msg)

  def receive(): String =
    retryConnectIfNoActiveConnection()
    try connectionOpt.get.read()
    catch case e: Throwable => 
      Terminal.showError(e, showStackTrace = isShowStackTrace)
      for c <- connectionOpt do try c.close() catch case e: Throwable => Terminal.showError(e)
      connectionOpt = None
      receive()

  def spawnReceiveLoop() = 
    Concurrent.Run:
      Terminal.putRed("spawnReceiveLoop() started in new thread!")
      Terminal.putGreen(s"Listening for messages until !stop in ${Thread.currentThread()}")
      while isListening.isTrue do
        val msg: String = receive()
        val i = msg.indexOf("msg=")
        if i < 1 then Terminal.putRed(msg) else
          Terminal.putGreen(msg.substring(0, i - 1) + " sent this message:")
          Terminal.put(msg.substring(i + 4))
      end while
      Terminal.putGreen("spawnReceiveLoop() thread done.")

  def helpText = """
    Type !listen to start concurrent receive loop 
    Type anything else to spam all connected clients with a message.
  """

  def commandLoop(): Unit = 
    var continue = true
    while continue do
      if isListening.isTrue 
      then Terminal.prompt("snack> ", color = Terminal.RED) 
      else Terminal.prompt("snack> ", color = Terminal.BLUE)
      val cmd = Terminal.get()
      if cmd == Terminal.CtrlD then continue = false 
      else if cmd == "" then println(helpText)
      else if cmd == "!listen" then 
        if isListening.isFalse then 
          isListening.setTrue()
          spawnReceiveLoop()  // TODO: better always receive and enqueue messages when not listenings
        else 
          Terminal.alert("Already in listening mode!")
          Terminal.putRed("Type !stop to stop listening.")
      else if cmd == "!stop" then
        Terminal.putGreen("Stopping listening mode...")
        isListening.setFalse()
      else 
        send(cmd)
    end while
    for c <- connectionOpt do c.close()

  def start(): Unit = 
    Terminal.putYellow(s"Attempting to connect to server host=$host port=$port")
    retryConnectIfNoActiveConnection()
    Terminal.putGreen("Successfully connected to snackomaten server!")
    Terminal.put(helpText)
    commandLoop()
  
end Client