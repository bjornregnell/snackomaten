package snackomaten

case class Message(userId: String, cmd: Message.Cmd, time: Timestamp, body: String):
  def encode(secretOpt: Option[String] = Some("unsecurePassword")): String = 
    import Message.Key.*
    val msg = java.lang.StringBuilder()
      .append(UserId.withValue(userId)).append(';')
      .append(Command.withValue(Message.Cmd.Send.toString)).append(';')
      .append(Time.withValue(Timestamp.now().encode)).append(';')
      .append(Body.withValue(body)).append(';')
    val et = Message.isEncryptedTag(secretOpt.isDefined)
    if secretOpt.isEmpty 
    then msg.insert(0, et).toString 
    else Crypto.AES.encryptString(secret = msg.toString, password = secretOpt.get).prepended(et)
  end encode


object Message:
  def apply(userId: String, cmd: Cmd, body: String): Message = 
    new Message(userId: String, cmd: Cmd, Timestamp.now(), body: String)

  def isEncryptedTag(isEncrypted: Boolean): Char = if isEncrypted then 'E' else 'C'

  enum DecodeError:
    case InvalidMessage, DecryptionFailed, InvalidUserId, InvalidCmd, InvalidTime, InvalidBody

  def decode(raw: String, secretOpt: Option[String] = Some("unsecurePassword")): Either[DecodeError, Message] = 
    if raw.length < 2 then Left(DecodeError.InvalidMessage) 
    else
      val decoded: Option[String] = 
        if raw(0) == Message.isEncryptedTag(false) then Some(raw.drop(1))
        else if secretOpt.isDefined then Crypto.AES.decryptString(raw.drop(1), secretOpt.get)
        else None
      if decoded.isEmpty then Left(DecodeError.DecryptionFailed) 
      else
        for
          u <- decoded.get.valueOf(Key.UserId).toRight(DecodeError.InvalidUserId)
          c <- decoded.get.valueOf(Key.Command).flatMap(Cmd.fromString).toRight(DecodeError.InvalidCmd)
          t <- decoded.get.valueOf(Key.Time).flatMap(Timestamp.decode).toRight(DecodeError.InvalidTime)
          b <- decoded.get.valueOf(Key.Body).toRight(DecodeError.InvalidBody)
        yield Message(u, c, t, b)
  end decode
          
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

