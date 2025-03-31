package snackomaten

object Database:
  case class User(uid: String)
  def loadUsers(): Concurrent.MutableKeyValueStore[String, User] = ???
  def saveUsers(): Unit = ???
  private val users: Concurrent.MutableKeyValueStore[String, User] = loadUsers()
  def getUser(uid: String): User = ???
  def updateUser(u: User): Unit = ???