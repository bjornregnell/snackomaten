package spamvas 

case class Message(text: String)
object Message:
  given messageCodec: Codec[Message] with
    extension (a: Message) def encode: Array[Byte] = summon[Codec[String]].encode(a.text)
    def fromBytes(as: Array[Byte]): Message = Message(summon[Codec[String]].fromBytes(as))