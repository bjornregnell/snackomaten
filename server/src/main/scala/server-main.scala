package snackomaten 

@main def ServerMain(port: Int, masterPassword: String) = 
  println("Hello snackomaten Server!")
  Config.checkJavaVersionOrAbort(minVersion = 21)
  println(s"Users in ${Server.UsersFile}")
  val s = Server(port, masterPassword)
  s.start()