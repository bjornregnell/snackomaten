package snackomaten 

@main def ClientMain(args: String*) = 
  Terminal.put("Welcome to the snackomaten terminal client! Ctrl+D to stop spamming ? for help")
  Terminal.prompt("enter userid: ")
  val userIdProposal = Terminal.get()
  val userId: String = 
    if userIdProposal.isEmpty then java.util.UUID.randomUUID().toString.take(5)
    else userIdProposal.filter(_ > ' ').take(10)
  Terminal.putGreen(s"Your userid is $userId") 

  val client = 
    if args.length == 1 then Client(userId, host = args(0))
    else if args.length == 2 && args(1).toIntOption.isDefined then Client(userId, host = args(0), port = args(1).toInt) 
    else Client(userId)

  client.start()