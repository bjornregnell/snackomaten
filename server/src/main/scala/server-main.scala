package snackomaten 

@main def ServerMain(port: Int, masterPassword: String) = 
  println("Hello snackomaten Server!")
  Config.checkJavaVersionOrAbort(minVersion = 21)
  val s = Server(port, masterPassword)
  s.start()