package snackomaten

/** Cryptographic utilities copy-pasted from https://github.com/bjornregnell/scryptic */
object Crypto:
  /** A Random Number Generator good enough for cryptographic usage (but slower than util.Random). */
  val RNG = java.security.SecureRandom()

  /** To encode and decode strings to/from strings or arrays of bytes using java.util.Base64 */ 
  object Base64:
    import java.util.Base64.{getDecoder, getEncoder}
    extension (s: String)
      def decodeToBase64Bytes: Array[Byte] = getDecoder.decode(s)
      def decodeToBase64String: String = String(s.decodeToBase64Bytes)
      def encodeToBase64Bytes: Array[Byte] = getEncoder.encode(s.getBytes)
      def encodeToBase64String: String = s.getBytes.encodeToBase64String

    extension (bytes: Array[Byte]) 
      def decodeToBase64Bytes: Array[Byte] = getDecoder.decode(bytes)
      def decodeToBase64String: String = String(bytes.decodeToBase64Bytes)
      def encodeToBase64Bytes: Array[Byte] = getEncoder.encode(bytes)
      def encodeToBase64String: String = getEncoder.encodeToString(bytes)
  end Base64

  /** Encode and decode objects to/from array of bytes.*/
  object Bytes:
    extension [T](bytes: Array[Byte]) def asObjectOfType: T =
      val bis = new java.io.ByteArrayInputStream(bytes)
      val ois = new java.io.ObjectInputStream(bis)
      try ois.readObject.asInstanceOf[T] finally ois.close

    extension [T](obj: T) def toByteArray: Array[Byte] =
      val bos = new java.io.ByteArrayOutputStream
      val oos = new java.io.ObjectOutputStream(bos)
      try
        oos.writeObject(obj)
        bos.toByteArray
      finally oos.close
  end Bytes

  /** A Secure Hash generator https://en.wikipedia.org/wiki/Secure_Hash_Algorithms */
  object SHA:
    val algorithm = "SHA-512"
    val underlying = java.security.MessageDigest.getInstance(algorithm)

    extension (s: String) def hash: String =
      import Base64.*
      val bytes = underlying.digest(s.encodeToBase64Bytes)
      bytes.encodeToBase64String

    extension (bytes: Array[Byte]) def digest: Array[Byte] = underlying.digest(bytes)
  end SHA

  /** A salt should be added to passwords before encryption to prevent that identical 
   * passwords get the same hash, see https://en.wikipedia.org/wiki/Salt_(cryptography)
  */
  object Salt:
    /** use init if you need to bootstrap or test with a predetermined salt */
    val init: String = "wUliyZmCxzu1Ecmw7/BhC4Sfw7hr5V4+/0HwXWx08go=" 

    /** Data returned by appendRandomSalt. */
    case class Salted(salted: String, salt: String, saltedHash: String)

    def nextRandomSalt(saltLength: Int = 32): String =
      val xs = new Array[Byte](saltLength)
      RNG.nextBytes(xs)
      Base64.encodeToBase64String(xs)

    extension (s: String) 
      def appendRandomSalt: Salted = 
        val theSalt = nextRandomSalt()
        Salted(salted = s ++ theSalt, salt = theSalt, saltedHash = SHA.hash(s ++ theSalt))
    
      def isValidSaltedSecret(salt: String, savedSaltedHash: String): Boolean = 
        SHA.hash(s ++ salt) == savedSaltedHash
  end Salt

  object PasswordGenerator:
    /** A pronounceable passphrase but needs to be long and should be changed after initial use. */
    def nextPassphrase(length: Int = 28): String =
      extension (xs: String) def pick: String = xs(RNG.nextInt(xs.length)).toString
      val vowels = "aeiouyåäö"
      val consonants = "bcdfghjklmnpqrstvwxz"
      val extras = "0123456789!?*+-"
      val sep = "-._"
      val xs = 
        for i <- 0 until (length / 4) -  (length / 16) - 1 yield 
          i % 4 match
            case 0 => consonants.pick + vowels.pick + consonants.pick + vowels.pick
            case 1 => vowels.pick + consonants.pick + consonants.pick + vowels.pick
            case 2 => consonants.pick + vowels.pick + vowels.pick + consonants.pick
            case _ => consonants.pick + vowels.pick + consonants.pick + consonants.pick
      val words = xs.map(s => if RNG.nextBoolean() then s else s.capitalize).mkString("", sep.pick, sep.pick)
      words ++ (1 to (length - words.length)).map(_ => extras.pick).mkString

    /** A secure password if long enough, but unfortunately difficult to remember. */
    def nextPassword(length: Int = 15, possibleChars: String = "0-9 A-Z a-z -!.,*+#<>%"): String =
      val chars: String = possibleChars.split(' ').toSeq.map {
          case s if s.size == 3 && s(1) == '-' => (s(0) to s(2)).mkString
          case s => s
        }.mkString
      def rndIndex() = RNG.nextInt(chars.size)
      val xs: Seq[Char] = (0 until length).map(_ => chars(rndIndex()))
      xs.mkString
  end PasswordGenerator


  /** Use DiffieHellman to establish a common secret over an insecure channel 
   * https://en.wikipedia.org/wiki/Diffie%E2%80%93Hellman_key_exchange
  */
  object DiffieHellman:
    case class KeyPair(publicKey: BigInt, privateKey: BigInt)
    
    val G = BigInt(2)  // used as base in modPow, should be a primitive root modulo P
    val DefaultBitLength = 2048  // should be at least 2048

    def probablePrime(bitLength: Int = DefaultBitLength): BigInt = 
      val c = 42  
      // probability of not being prime is 1.0 - math.pow(0.5, c)
      // a higher certainty than around 42 will not be better by Double precision
      BigInt(bitLength, certainty = c, RNG)  

    def generateKeyPair(prime: BigInt, bitLength: Int = DefaultBitLength): KeyPair =
      val secret = BigInt(bitLength, RNG)
      KeyPair(G.modPow(secret, prime), secret) 

    def sharedSecret(otherPublicKey: BigInt, mySecretKey: BigInt, prime: BigInt): BigInt =
      otherPublicKey.modPow(mySecretKey, prime)
  end DiffieHellman

  /** Encryption based on https://en.wikipedia.org/wiki/Advanced_Encryption_Standard */
  object AES:
    import javax.crypto.spec.SecretKeySpec
    import javax.crypto.{Cipher, SealedObject}

    val (algorithm, keyLength) = ("AES", 128)

    private def keySpec(password: String): SecretKeySpec =
      val key = SHA.digest(Base64.encodeToBase64Bytes(password)).take(keyLength/8)
      new SecretKeySpec(key, algorithm)

    private def makeEncrypter(password: String): Cipher=
      val enc = Cipher.getInstance(algorithm)
      enc.init(Cipher.ENCRYPT_MODE, keySpec(password))
      enc

    private def makeDecrypter(password: String): Cipher=
      val enc = Cipher.getInstance(algorithm)
      enc.init(Cipher.DECRYPT_MODE, keySpec(password))
      enc

    def encryptSerializable(obj: java.io.Serializable, password: String): SealedObject =
      new SealedObject(obj, makeEncrypter(password))

    def decryptSealedObject[T](sealedObject: SealedObject, password: String): Option[T] =
      util.Try{ sealedObject.getObject(makeDecrypter(password)).asInstanceOf[T] }.toOption

    def encryptObjectToString[T](obj: T, password: String): String =
      val bytes = Bytes.toByteArray(obj)
      val b64 = Base64.encodeToBase64String(bytes)
      val sealedObject = encryptSerializable(b64, password)
      val bytesOfSealed = Bytes.toByteArray(sealedObject)
      val encrypted   = Base64.encodeToBase64String(bytesOfSealed)
      encrypted

    def decryptObjectFromString[T](encrypted: String, password: String): Option[T] =
      util.Try {
        val bytesOfSealed = Base64.decodeToBase64Bytes(encrypted)
        val sealedObject  = Bytes.asObjectOfType[SealedObject](bytesOfSealed)
        val b64 = decryptSealedObject[String](sealedObject, password).get
        val bytes = Base64.decodeToBase64Bytes(b64)
        val obj = Bytes.asObjectOfType[T](bytes)
        obj
      }.toOption

    def encryptString(secret: String, password: String): String =
      val sealedObject = encryptSerializable(secret, password)
      Base64.encodeToBase64String(Bytes.toByteArray(sealedObject))

    def decryptString(encrypted: String, password: String): Option[String] = util.Try {
      val bytes = Base64.decodeToBase64Bytes(encrypted)
      val sealedObject = Bytes.asObjectOfType[SealedObject](bytes)
      decryptSealedObject[String](sealedObject, password).get
    }.toOption

  end AES