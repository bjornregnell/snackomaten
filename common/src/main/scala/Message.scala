package snackomaten

case class Message(text: String):
  import Message.*

  private def get(key: String): Option[String] =
    val keyString = s"$key="
    val startPos = text.indexOf(keyString)
    if startPos < 0 then None else 
      val untilPos = if key == "bdy" then text.length else
        val i = text.indexOf(';', startPos)
        if i < 0 then text.length else i
      Some(text.substring(startPos + keyString.length, untilPos))

  private def get(key: Message.Key): Option[String] = get(key.keyString)

  lazy val userId: Option[String] = get(Key.UserId)
  lazy val cmd:    Option[Cmd]    = get(Key.Command).flatMap(Cmd.fromString)
  lazy val body:   Option[String] = get(Key.Body)

  lazy val isValid: Boolean = userId.nonEmpty && cmd.nonEmpty && body.nonEmpty

object Message:
  enum Key(val keyString: String):
    case UserId      extends Key("uid")
    case Command     extends Key("cmd")
    case Body        extends Key("bdy")
    
    def apply(s: String): String = s"$keyString=$s"

  enum Cmd { case Ping, Login, Send, EchoOn, EchoOff }
  object Cmd:
    def fromString(s: String): Option[Cmd] = 
      try Some(valueOf(s)) catch case e: IllegalArgumentException => None

  def apply(userId: String, cmd: Cmd, body: String): Message = 
    Message(Key.UserId(userId) + ";" + Key.Command(Cmd.Send.toString) + ";" + Key.Body(body))