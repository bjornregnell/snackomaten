package snackomaten 

object Codec:
  // TODO: always encrypt/decrypt when encode/decode
  def decode(bytes: Array[Byte]): String = String(bytes, java.nio.charset.StandardCharsets.UTF_8)
  def encode(s: String): Array[Byte] = s.getBytes()
