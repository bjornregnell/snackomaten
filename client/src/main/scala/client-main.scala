package snackomaten 

@main def ClientMain(args: String*) = 
  Terminal.put("Welcome to the snackomaten terminal client! Ctrl+D to stop spamming ? for help")
  Config.checkJavaVersionOrAbort(minVersion = 21)

  if args.contains("-h") then 
    Terminal.putYellow("Help: give zero or more arguments in this order: masterpassword host port userName")
    sys.exit(0)
  end if
  
  def generateUserName() = java.util.UUID.randomUUID().toString.take(7)
    
  def read(what: String, maxLength: Int = 100, isSecret: Boolean = false) = 
    Terminal.putGreen(what)
    val input = if !isSecret then Terminal.awaitInput() else Terminal.awaitSecret()
    input.filter(_ > ' ').filterNot(_ == '=').filterNot(_ == ';').take(maxLength)
  
  val client = 
    Client(
      masterPassword = args.lift(0).getOrElse(read("Enter password:", isSecret = true)),
      host = args.lift(1).getOrElse("bjornix.cs.lth.se"),
      port = args.lift(2).flatMap(_.toIntOption).getOrElse(8090),
      userId = args.lift(3).getOrElse(generateUserName()),
    )

  client.start()