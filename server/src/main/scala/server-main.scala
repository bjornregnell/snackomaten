package snackomaten 

@main def ServerMain(port: Int) = 
  println("Hello snackomaten Server!")
  Config.checkJavaVersionOrAbort(minVersion = 21)
  val s = Server(port)
  s.start()