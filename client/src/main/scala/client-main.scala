package snackomaten 

@main def ClientMain(args: String*) = 
  Terminal.put("Welcome to the snackomaten terminal client! Ctrl+D to stop spamming ? for help")
  Config.checkJavaVersionOrAbort(minVersion = 21)

  if args.contains("-h") || args.contains("--help") || args.contains("-v") || args.contains("--version") then 
    Terminal.put(s"Welcome to snackomaten version ???")
    Terminal.putYellow("use no or optional args: --password mypassword --host localhost --port 8090 --user MyUserName")
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
  
  val userName: String = arg("user").getOrElse:
    val correct = "(one word, only letters or digits, max 30 chars)"
    def read(prompt: String) = 
      readUntilValid(prompt, errMsg = s"must be non-empty and only letters and digits and max 30 characters!", 
        isSecret = false, maxLen = 30)(input => input.nonEmpty && input.forall(ch => ch.isLetterOrDigit))

    val savedUsers = Config.userDirsInConfigDir()
    if savedUsers.length == 1 then 
      savedUsers.head
    else if savedUsers.length > 1 then  
      Terminal.putYellow(s"Configs exists for these users: ${savedUsers.mkString(",")}")
      read(s"Enter existing or new user nick name $correct:")
    else 
      read(s"Enter user nick name $correct:")

  val config = Config(userName)
  Disk.createDirIfNotExist(config.Store.configDir)

  Terminal.putYellow(s"Users with config in ${Config.configBaseDir}: ${Config.userDirsInConfigDir().mkString(",")}")

  val mpw = arg("password").getOrElse:
    val input = readUntilValid(s"Enter password for $userName:", "must be non-empty!", isSecret = true)(_.nonEmpty)
    val hash = Crypto.SHA.hash(input)
    println(s"DEBUG 1: hash=$hash")
    println(s"DEBUG 2: config.passwordHashOpt=${config.passwordHashOpt}")
    println(s"DEBUG 3: config.currentConfig=${config.Store.currentConfig.toSeq}")

    if config.passwordHashOpt.isEmpty then 
      config.setPasswordHash(hash)
      println(s"DEBUG 4: config.currentConfig=${config.Store.currentConfig.toSeq}")
      Terminal.put("Do you want to see your password so you can copy paste it to a password manager? (Y/n)")
      if Terminal.awaitInput().toLowerCase.startsWith("y") then Terminal.put(input)
    else 
      println(s"DEBUG: config.currentConfig=${config.Store.currentConfig.toSeq}")
      if hash != config.passwordHashOpt.get then
        Terminal.putRed("Bad password!")
        sys.exit(1)
      else Terminal.putGreen("Password OK!")
    end if
    input  

  val host = arg("host").getOrElse(config.globalHost)
  val port = arg("port").flatMap(_.toIntOption).getOrElse(config.globalPort)

  val client = Client(config = config, masterPassword = mpw, host = host, port = port, userId = userName)

  client.start()