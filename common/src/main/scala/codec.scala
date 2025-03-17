package snackomaten 

trait Codec[A]:
  extension (a: A) def encode: Array[Byte]
  def fromBytes(as: Array[Byte]): A

extension (as: Array[Byte]) def decode[A](using c: Codec[A]): A = c.fromBytes(as)

object Codec:
  given stringCodec: Codec[String] with
    extension (a: String) def encode: Array[Byte] = a.getBytes()
    def fromBytes(as: Array[Byte]): String = String(as, java.nio.charset.StandardCharsets.UTF_8)

