package spamvas 

@main def ClientMain(args: String*) = 
  println("Hello Spamvas Client!     Ctrl+D to stop spamming")
  val c = Client()
  c.start()