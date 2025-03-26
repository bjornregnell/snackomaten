package snackomaten 

@main def ClientMain(args: String*) = 
  Terminal.put("Welcome to the snackomaten terminal client! Ctrl+D to stop spamming ? for help")
  Config.checkJavaVersionOrAbort(minVersion = 21)

  val uidMaxLength = 25
  
  Terminal.prompt(s"enter user id: ")
  val userIdProposal = Terminal.awaitInput()
  val userId: String = 
    if userIdProposal.isEmpty then java.util.UUID.randomUUID().toString.take(5)
    else userIdProposal.filter(_ > ' ').filterNot(_ == '=').filterNot(_ == ';').take(uidMaxLength)
  Terminal.putGreen(s"Your userid is $userId") 

  val client = 
    if args.length == 1 then Client(userId, host = args(0))
    else if args.length == 2 && args(1).toIntOption.isDefined then Client(userId, host = args(0), port = args(1).toInt) 
    else Client(userId)

  client.start()