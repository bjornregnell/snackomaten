package snackomaten

import java.io.BufferedWriter
import os.exists
import java.io.Closeable

object SecureKeyValueStore:
  import Crypto.*
  import java.io.File

  case class MasterSecret(salt: String, saltedHash: String)

  case class MasterSecretValidation(isValid: Boolean, isFileCreated: Boolean, salt: String)

  extension (s: String) 
    def saveStringTo(file: File, enc: String = "UTF-8"): Unit = 
      val w = java.io.PrintWriter(file, enc)
      try w.write(s) finally w.close()
    
    def appendStringTo(file: File, enc: String = "UTF-8"): Unit = 
      val w = 
        if file.exists() then
          import java.nio.charset.Charset
          java.io.BufferedWriter(java.io.FileWriter(file, Charset.forName(enc), true))
        else java.io.PrintWriter(file, enc)
      try w.write(s) finally w.close()

  extension (file: File) def loadString(enc: String = "UTF-8"): String = 
      val s = scala.io.Source.fromFile(file, enc)
      try s.mkString finally s.close

  extension (file: File) def loadLines(enc: String = "UTF-8"): Seq[String] = 
      val s = scala.io.Source.fromFile(file, enc)
      try s.getLines().toSeq finally s.close

  def saveMasterPassword(mpwFile: File, mpw: String): String =
    val salt = Salt.nextRandomSalt()
    val m = MasterSecret(salt, SHA.hash(mpw + salt))
    val encrypted = AES.encryptObjectToString(m, mpw)
    encrypted.saveStringTo(mpwFile)
    salt

  def checkMasterPassword(mpwFile: File, mpw: String): MasterSecretValidation =
    if mpwFile.exists then
      val encrypted = mpwFile.loadString()
      val MasterSecret(masterSalt, saltedHash) =
        AES.decryptObjectFromString[MasterSecret](encrypted, mpw).getOrElse(MasterSecret("", ""))
      if SHA.hash(mpw + masterSalt) == saltedHash 
      then MasterSecretValidation(isValid = true, isFileCreated = false, salt = masterSalt)
      else MasterSecretValidation(isValid = false, isFileCreated = false, salt = "")
    else
      mpwFile.getParentFile.mkdirs()
      mpwFile.createNewFile()
      val masterSalt = saveMasterPassword(mpwFile, mpw)
      MasterSecretValidation(isValid = true, isFileCreated = true, salt = masterSalt)

  
  object Vault:
    case class VaultOpt[K <: Serializable, V <: Serializable](
      vaultOpt: Option[Vault[K, V]], isMasterPasswordFileCreated: Boolean, saltOpt: Option[String]
      )

    /** Open a vault after authentication, give explicit key and value type params [K, V] or else Nothing is inferred! */
    def openVaultOf[K <: Serializable, V <: Serializable](
      masterPw: String, 
      mpwFile: File, 
      vaultFile: File, 
      tmpFile: File,
    ): VaultOpt[K, V] =
      val MasterSecretValidation(isValid, isMpwFileCreated, salt) = 
        checkMasterPassword(mpwFile, masterPw)
      if !isValid then VaultOpt(None, isMpwFileCreated, Some(salt)) 
      else
        val vault = Vault[K, V](masterPw, masterSalt = salt, mpwFile, vaultFile, tmpFile)
        VaultOpt(Some(vault), isMpwFileCreated, saltOpt = None)
  
  class Vault[K <: Serializable, V <: Serializable] private (
    masterPassword: String, 
    masterSalt: String,
    masterPasswordFile: File,
    vaultFile: File,
    tempFile: File
  ) extends Closeable:
    val lockFile = File(vaultFile.getParentFile().getAbsolutePath() + "/" + vaultFile.getName() + ".LOCK")

    private val isLocked =
      assert(!lockFile.exists(), 
        s"Lock file exists: $lockFile\nThis indicates a previous crash or Vault opened by other user. Delete lock file and restart.")
      s"Lock file of vault: $lockFile".saveStringTo(lockFile)
      java.util.concurrent.atomic.AtomicBoolean(true)

    private val _isAuthenticated = synchronized:
      val check = checkMasterPassword(mpwFile = masterPasswordFile, mpw = masterPassword)
      java.util.concurrent.atomic.AtomicBoolean(check.isValid)

    def isAuthenticated: Boolean = _isAuthenticated.get()
      
    import scala.jdk.CollectionConverters.*
    import scala.collection.concurrent.{Map as CMap}

    private lazy val store: CMap[K, V] = 
      if !vaultFile.exists() && lockFile.exists() then 
        val initStore = java.util.concurrent.ConcurrentHashMap[K, V]().asScala // empty
        applyAllPendingUpdatesAndClearTemp(initStore)
        saveAllOverwriteVaultFile(initStore)
        initStore
      else 
        val encrypted = vaultFile.loadString()
        val storeOpt = AES.decryptObjectFromString[CMap[K, V]](encrypted, password = masterPassword)
        if storeOpt.isEmpty then 
          _isAuthenticated.set(false)
          java.util.concurrent.ConcurrentHashMap[K, V]().asScala // empty if authentication failed
        else
          _isAuthenticated.set(true)
          applyAllPendingUpdatesAndClearTemp(storeOpt.get)
          saveAllOverwriteVaultFile(storeOpt.get)
          storeOpt.get
        end if 
      end if

    private def applyAllPendingUpdatesAndClearTemp(s: CMap[K, V]): Unit = synchronized:
      if tempFile.exists() then  
        val xs = tempFile.loadLines()
        if xs.nonEmpty then
          for encrypted <- xs do
            val t2Opt = AES.decryptObjectFromString[Tuple2[K, V]](encrypted, password = masterPassword)
            if t2Opt.isDefined 
            then s.addOne(t2Opt.get._1, t2Opt.get._2)
            else () // silently ignore failed decryptions ???
          end for
          "".saveStringTo(tempFile)  // always clear temp even if some encryptions failed ???
        end if
      else ()

    private def saveAllOverwriteVaultFile(s: CMap[K, V]): Unit = synchronized:
      if isAuthenticated && lockFile.exists() then
        val encrypted = AES.encryptObjectToString(s, password = masterPassword)
        encrypted.saveStringTo(vaultFile)

    def save(): Boolean = synchronized:
      if isAuthenticated && lockFile.exists() then
        applyAllPendingUpdatesAndClearTemp(store)
        saveAllOverwriteVaultFile(store)
        true
      else false

    private def appendUpdateToTempFile(key: K, value: V): Boolean = synchronized:
      if isAuthenticated && lockFile.exists() then 
        val encrypted = AES.encryptObjectToString(Tuple2(key, value), password = masterPassword)
        encrypted.appendStringTo(tempFile)
        true
      else false

    def get(key: K): Option[V] = store.get(key)

    def getOrElse(key: K, default: => V): V = store.getOrElse(key, default)
    
    def getOrElseUpdate(key: K, default: => V): V = synchronized:
      store.getOrElseUpdate(key, default)
    
    def addOneAndSavePending(key: K, value: V): Boolean = synchronized:
      store.addOne(key, value)
      appendUpdateToTempFile(key, value)

    def close(): Unit = 
      save()
      if lockFile.exists() then lockFile.delete() else ()
