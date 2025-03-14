package spamvas 

@main def ServerMain(port: Int) = 
  println("Hello Spamvas Server!")
  val s = Server(port)
  s.start()