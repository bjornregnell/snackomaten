package snackomaten

case class Message(userId: String, cmd: Message.Cmd, time: Timestamp, body: String):
  def encode(pwd: String = "unsecurePassword"): String = 
    import Message.Key.*
    UserId.withValue(userId) + ";" 
    + Command.withValue(Message.Cmd.Send.toString) + ";" 
    + Time.withValue(Timestamp.now().encode) + ";" 
    + Body.withValue(Crypto.AES.encryptString(secret = body, password = pwd))

object Message:
  def apply(userId: String, cmd: Cmd, body: String): Message = 
    new Message(userId: String, cmd: Cmd, Timestamp.now(), body: String)

  enum DecodeError:
    case InvalidUserId, InvalidCmd, InvalidTime, InvalidBody

  def decode(raw: String, pwd: String = "unsecurePassword"): Either[DecodeError, Message] = 
    for
      u <- raw.valueOf(Key.UserId).toRight(DecodeError.InvalidUserId)
      c <- raw.valueOf(Key.Command).flatMap(Cmd.fromString).toRight(DecodeError.InvalidCmd)
      t <- raw.valueOf(Key.Time).flatMap(Timestamp.decode).toRight(DecodeError.InvalidTime)
      b <- raw.valueOf(Key.Body)
            .flatMap(encrypted => Crypto.AES.decryptString(encrypted, pwd))
            .toRight(DecodeError.InvalidBody)
    yield Message(u, c, t, b)
          
  enum Key:
    case UserId, Command, Time, Body
    def keyString = this.toString
    def withValue(value: String): String = s"$keyString=$value"

  enum Cmd:
    case Ping, Login, Send 

  object Cmd:
    def fromString(s: String): Option[Cmd] = 
      try Some(valueOf(s)) catch case e: IllegalArgumentException => None

  extension (raw: String)
    def valueOf(key: Message.Key): Option[String] = 
      val keyString = s"${key.keyString}="
      val startPos = raw.indexOf(keyString)
      if startPos < 0 then None else 
        val untilPos = if keyString == Key.Body.keyString then raw.length else
          val i = raw.indexOf(';', startPos)
          if i < 0 then raw.length else i
        Some(raw.substring(startPos + keyString.length, untilPos))

