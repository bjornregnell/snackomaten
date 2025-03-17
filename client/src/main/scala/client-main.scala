package snackomaten 

@main def ClientMain(args: String*) = 
  println("Hello snackomaten Client!     Ctrl+D to stop spamming")
  val c = Client()
  c.start()