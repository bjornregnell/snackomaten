package snackomaten 

@main def ServerMain(port: Int) = 
  println("Hello snackomaten Server!")
  val s = Server(port)
  s.start()