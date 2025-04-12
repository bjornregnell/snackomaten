package snackomaten

case class User(
  userId: String, 
  email: String, 
  fullName: String, 
  chosenPassword: Option[String] = None, 
  personalInvite: Option[String] = None,
)
object User:
  def fromConfig() = ???