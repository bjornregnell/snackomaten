package snackomaten

case class Message(userId: String, cmd: Message.Cmd, time: Timestamp, body: String):
  def encode(secretOpt: Option[String] = Some("unsecurePassword")): String = 
    import Message.Key.*, Message.FieldSep
    val msg = java.lang.StringBuilder()
      .append(UserId.withValue(userId))                    .append(FieldSep)
      .append(Command.withValue(Message.Cmd.Send.toString)).append(FieldSep)
      .append(Time.withValue(Timestamp.now().encode))      .append(FieldSep)
      .append(Body.withValue(body))

    val et = Message.isEncryptedTag(secretOpt.isDefined)
    if secretOpt.isEmpty 
    then msg.insert(0, et).toString 
    else Crypto.AES.encryptString(secret = msg.toString, password = secretOpt.get).prepended(et)
  end encode


object Message:
  def apply(userId: String, cmd: Cmd, body: String): Message = 
    new Message(userId: String, cmd: Cmd, Timestamp.now(), body: String)

  def sendMsg(userId: String, body: String): Message = apply(userId, Cmd.Send, body)

  def isEncryptedTag(isEncrypted: Boolean): Char = if isEncrypted then 'E' else 'C'

  val FieldSep = ';'
  val KeyValueSep = '='
  val illegalFieldChars = Set(Message.KeyValueSep, Message.FieldSep)

  enum DecodeError:
    case InvalidMessage
    case BodyNotLast
    case DecryptionFailed
    case InvalidKey(key: Key)

  /** Decrypt a raw message (if encrypted) and parse it into a Message by searching for each Key. */
  def decode(raw: String, secretOpt: Option[String] = Some("unsecurePassword")): Either[DecodeError, Message] = 
    // a hand-rolled parser aimed at being rather fast and give good parsing errors
    // aborts if Key.Body is not the last key and if any key is missing or non-empty
    if raw.length < 2 then Left(DecodeError.InvalidMessage) 
    else
      val clearTextOpt: Option[String] = 
        if raw(0) == Message.isEncryptedTag(false) then Some(raw.drop(1))
        else if secretOpt.isDefined then Crypto.AES.decryptString(raw.drop(1), secretOpt.get)
        else None
      if clearTextOpt.isEmpty then Left(DecodeError.DecryptionFailed) 
      else
        val msg: String = clearTextOpt.get
        val keys: Array[Key] = Key.values
        val keyStarts: Array[Int] = keys.map(k => msg.indexOf(k.keyString))
        val notFound: Int = keyStarts.indexWhere(_ < 0)
        if notFound >= 0 then Left(DecodeError.InvalidKey(keys(notFound)))
        else 
          val bodyStart = keyStarts.last
          if bodyStart < 0 then Left(DecodeError.InvalidKey(Key.Body)) 
          else if bodyStart < SumOfKeyLengths then Left(DecodeError.BodyNotLast)
            inline def at(k: Key): Int = keyStarts(k.ordinal)
            
            inline def getValue(k: Key): Option[String] = 
              val from: Int = at(k)
              val value = msg.substring(from + k.keyString.length, msg.indexOf(FieldSep, from))
              if value.isEmpty then None else Some(value)

            for
              u <- getValue(Key.UserId).toRight(DecodeError.InvalidKey(Key.UserId))
              c <- getValue(Key.Command).flatMap(Cmd.fromString).toRight(DecodeError.InvalidKey(Key.Command))
              t <- getValue(Key.Time).flatMap(Timestamp.decode).toRight(DecodeError.InvalidKey(Key.Time))
              b <- Right(msg.substring(bodyStart + Key.Body.keyString.length))
            yield Message(u, c, t, b)
  end decode
  
  /** A message must contain all these keys and Key.Body must be the last key. */
  enum Key: 
    case UserId, Command, Time, Body // Body must be the last case for Message.decode to work properly
    def keyString = toString + KeyValueSep
    def withValue(value: String): String = s"$keyString$value"
  
  val SumOfKeyLengths: Int = Key.values.map(_.keyString).mkString.length

  /** Values associated with Key.Command */
  enum Cmd:
    case Ping, Init, Login, Send 

  object Cmd:
    def fromString(s: String): Option[Cmd] = 
      try Some(valueOf(s)) catch case e: IllegalArgumentException => None

