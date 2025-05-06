package snackomaten

/** Registered User data. Names and email from Ladok. The id is the Crypto.SHA.hash of personnummer in Ladok. */
case class User(
  givenName: String,
  familyName: String,
  email: String,
  id: String,
):
  def showName: String = s"$givenName.$familyName"

object User:
  val Separator: Char = '\t'

  /** Create a User from a string with fields separated by Separator */
  def fromSepValues(separatedValues: String): Option[User] = 
    val xs = separatedValues.split(Separator)
    try Some(User(xs(0), xs(1), xs(2), xs(3))) catch 
      case e: IndexOutOfBoundsException => None
  
  /** Create a String with all User fields separated by Separator */
  def toSepValues(user: User): String = user.productIterator.mkString(Separator.toString)