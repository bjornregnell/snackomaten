package snackomaten 

@main def ClientMain(args: String*) = 
  Terminal.put("Welcome to the snackomaten terminal client! Ctrl+D to stop spamming ? for help")
  val c = 
    if args.length == 1 then Client(host = args(0))
    else if args.length == 2 && args(1).toIntOption.isDefined then Client(host = args(0), port = args(1).toInt) 
    else Client()

  c.start()