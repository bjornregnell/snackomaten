package snackomaten 

case class Message(text: String):
  def encode: Array[Byte] = Codec.encode(text)
  def isValid: Boolean = true //TODO check that text has all valid parts

object Message:
    def decode(bytes: Array[Byte]): Message = Message(Codec.decode(bytes))