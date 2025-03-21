package snackomaten

case class Message(text: String) extends Comparable[Message]:

  def compareTo(other: Message): Int = text.compareTo(other.text)
  
  def get(key: String): Option[String] =
    val keyString = s"$key="
    val startPos = text.indexOf(keyString)
    if startPos < 0 then None else 
      val untilPos = if key == "msg" then text.length else
        val i = text.indexOf(';', startPos)
        if i < 0 then text.length else i
      Some(text.substring(startPos + keyString.length, untilPos))