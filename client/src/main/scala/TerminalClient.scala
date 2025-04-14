package snackomaten 

object TerminalClient:
  def apply(args: Seq[String]) = 
    Terminal.put("Welcome to the snackomaten terminal client! Ctrl+D to stop spamming ? for help")
    Config.checkJavaVersionOrAbort(minVersion = 21)

    if args.contains("-h") || args.contains("--help") || args.contains("-v") || args.contains("--version") then 
      Terminal.put(s"Welcome to snackomaten version ???")
      Terminal.putYellow("optional args: --password mypassword --host localhost --port 8090 --user MyUserName")
      sys.exit(0)
    end if

    val argsMap: Map[String, String] = 
      args.sliding(2).filter(xs => xs(0).startsWith("-")).map(xs => xs(0).dropWhile(_ == '-') -> xs(1)).toMap

    def arg(key: String): Option[String] = argsMap.get(key)

    def generateUserName() = java.util.UUID.randomUUID().toString.take(7)

    def readUntilValid(prompt: String, errMsg: String, isSecret: Boolean, maxLen: Int = 100)(isValid: String => Boolean) = 
      Terminal.putGreen(prompt)
      def read() = 
        val in = if !isSecret then Terminal.awaitInput() else Terminal.awaitSecret()
        if in == Terminal.CtrlD then sys.exit(0)
        in
      end read
      var input = read()
      while !isValid(input) do 
        Terminal.putRed(s"Input is not valid: $errMsg")
        Terminal.putYellow(prompt)
        input = read()
      end while
      input.filter(_ >= ' ').take(maxLen)
    end readUntilValid
    
    val savedUsers = Config.userDirsInConfigDir()

    val userName: String = arg("user").getOrElse:
      val correct = "(one word, only letters or digits, max 30 chars)"
      def read(prompt: String) = 
        readUntilValid(prompt, errMsg = s"must be non-empty and only letters and digits and max 30 characters!", 
          isSecret = false, maxLen = 30)(input => input.nonEmpty && input.forall(ch => ch.isLetterOrDigit))
      end read
      if savedUsers.length == 1 then 
        savedUsers.head
      else if savedUsers.length > 1 then  
        Terminal.putYellow(s"Configs exists for these users: ${savedUsers.mkString(",")}")
        read(s"Enter existing or new user nick name $correct:")
      else 
        read(s"Enter user nick name $correct:")
      end if

    val config = Config(userName)

    Disk.createDirIfNotExist(config.Store.configDir)

    val isNewUser = !savedUsers.contains(userName) 

    if isNewUser then Terminal.putYellow(s"Adding new user config: ${config.Store.configFileName}")
    Terminal.putYellow(s"Users with config in ${Config.configBaseDir}/\n${Config.userDirsInConfigDir().mkString("\n")}")
    
    var hashOpt: Option[String] = None

    val mpw = arg("password").getOrElse:
      val input = readUntilValid(s"Enter password for $userName:", "must be non-empty!", isSecret = true)(_.nonEmpty)
      val hashOpt = Some(Crypto.SHA.hash(input))
      if config.passwordHashOpt.isEmpty then 
        config.setPasswordHash(hashOpt.get)
        Terminal.put("Show password so you can copy paste it to a password manager? (Y/n)")
        if Terminal.awaitInput().toLowerCase.startsWith("y") then 
          Terminal.putYellow("You typed this password:")
          Terminal.put(input)
      end if
      input

    if hashOpt.isEmpty then hashOpt = Some(Crypto.SHA.hash(mpw))

    if config.passwordHashOpt.nonEmpty && hashOpt.nonEmpty && hashOpt.get != config.passwordHashOpt.get then
      Terminal.putRed("Bad password! Entered password hash does not match stored hash.")
      sys.exit(1)
    else if isNewUser then Terminal.putGreen("Password hash saved!")
    else Terminal.putGreen("Password OK!")

    val host = arg("host").getOrElse(config.globalHost)
    val port = arg("port").flatMap(_.toIntOption).getOrElse(config.globalPort)

    val client = new TerminalClient(config = config, masterPassword = mpw, host = host, port = port, userId = userName)

    client.start()

  end apply

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

  def writeTextToServer(text: String): Unit =
    try connectionOpt.get.write(text) 
    catch case e: Throwable => 
      Terminal.putRed(s"Error during send(): $e")
      closeConnection()
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
  def sendLoop(): Unit = 
    Thread.sleep(500) // wait a bit to allow receive loop to start
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
        val msg = Message.sendText(userId = userId, body = input)
        Terminal.putGreen(s"Sending '$msg' via $connectionOpt: isActive=$isActive")
        writeTextToServer(msg.encode())
    end while
    quit.setTrue()
    closeConnection()
    Terminal.putGreen(s"Goodbye $userId! snackomaten.Client terminates")

  object login:
    import Crypto.{DiffieHellman as DH} 
    lazy val sessionId = DH.probablePrime()
    lazy val clientKeys = DH.generateKeyPair(sessionId)

    private val atomicSecret = java.util.concurrent.atomic.AtomicReference[BigInt](null)

    def setSharedSecret(serverKeys: DH.KeyPair, clientKeys: DH.KeyPair) = 
      atomicSecret.set(DH.sharedSecret(serverKeys.publicKey, clientKeys.privateKey, sessionId))
    
    def getSharedSecret(): Option[BigInt] = Option(atomicSecret.get())

    def apply(): Unit =
      if connectionOpt.isEmpty then awaitConnection() 
      connectionOpt match
        case None => Terminal.putRed("No connection."); sys.exit(1)
        case Some(connection) =>
          val body = s"$sessionId${Message.FieldSep}${clientKeys.publicKey}"
          writeTextToServer(Message(userId, Message.Cmd.Connect, body).encode(secretOpt = None))
          /* TODO 

            on client side: 
              1. await Confirm from server with serverKeys.publicKey in body
              2. compute and remember shared secret 
              3. send encrypted with shared secret Login msg with password hash in body 
              4. await login confirmation 
            
            on server side:
              1. do not broadcast Connection message, but handle Connection msg and remember sessionId+clientKeys.publicKey
              2. compute shared secret and send back confirmation with serverKeys.publicKey 
              3. await encrypted Login message with password hash and store if new user or validate password hash if existing
              4. if authorized then send login confirmation or close connection
          */

  
  /** Starts this client. Awaits connection to server before `spawnReceiveLoop()` and `commandLoop()`. */
  def start(): Unit = 
    Terminal.putYellow(s"Connecting to snackomaten.Server host=$host port=$port")
    login()
    Terminal.putYellow(helpText)
    Terminal.putGreen("Press ENTER to toggle buffering mode.\nYou may want to start a another client in another terminal window with buffering mode toggled ON so you can type and send your messages without being interrupted by any incoming messages.")
    spawnReceiveLoop()
    sendLoop()
  
end TerminalClient