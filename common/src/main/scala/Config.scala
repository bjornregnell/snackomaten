package snackomaten 

object Config:
  val configBaseDir = 
    val dir = Disk.userDir() + s"/snackomaten/users"
    Disk.createDirIfNotExist(dir)
    dir
  
  def userDirsInConfigDir(): Seq[String] = 
    Disk.list(configBaseDir).filter(f => java.io.File(s"$configBaseDir/$f").isDirectory())

  val default: Map[String, String] = Map(
      "globalHost" -> "bjornix.cs.lth.se",
      "globalPort" -> "8090",
    ) 

  def isValidKey(key: String): Boolean = 
    key.trim.nonEmpty && key.forall(c => c.isLetter || c.isDigit || c == '_' || c == '.' || c == '/')
  
  def badKeys(kvs: Map[String, String]): Seq[(String, String)] = kvs.filterNot((k, v) => isValidKey(k)).toSeq

  assert(badKeys(default).isEmpty, s"illegal chars Config.default: ${badKeys(default)}")

  def javaMajorVersion(): Option[Int] = 
    Option(System.getProperty("java.version")).flatMap(_.takeWhile(_.isDigit).toIntOption)

  def checkJavaVersionOrAbort(minVersion: Int): Unit =
      for version <- javaMajorVersion() if version < minVersion do 
        Terminal.alert(s"Snackomaten requires at least Java 21")
        throw Error("Java upgrade needed.")

class Config(userName: String):
  import Config.* 

  def globalHost: String = Store.getString("globalHost").orElse(default.get("globalHost")).get
  def globalPort: Int    = Store.getInt("globalPort").orElse(default.get("globalPort").flatMap(_.toIntOption)).get
  def passwordHashOpt: Option[String] = Store.getString("passwordHash")
  def setPasswordHash(hash: String): Unit = Store.setString("passwordHash", hash)

  object Store:
    val configDir = s"$configBaseDir/$userName"

    val configFileName = s"$configDir/config.txt"

    private def overwriteConfigWithDefault(): Unit =
      Disk.saveLines(lines = default.map((k,v) => s"$k=$v").toSeq, fileName = configFileName)

    private def loadConfigFromDiskOrCreate(): Seq[(String, String)] = 
      Disk.createDirIfNotExist(configDir)
      
      def splitKeyValue(s: String): Option[(String, String)] = 
        val i = s.indexOf("=")
        if i < 1 || s.length < 2 then 
          Terminal.alert(s"Ignoring bad setting in $configFileName: $s")
          None
        else 
          val (k, v) = (s.substring(0, i), s.substring(i + 1))
          if isValidKey(k) then Option(k -> v) else 
            Terminal.alert(s"Ignoring bad key in $configFileName: $k")
            None

      if !Disk.isExisting(configFileName) then 
        println(s"DEBUG loadConfigFromDiskOrCreate: Disk.isExisting(configFileName) = ${Disk.isExisting(configFileName) }")
        overwriteConfigWithDefault()
        default.toSeq
      else 
        Disk.loadLines(fileName = configFileName).flatMap(splitKeyValue)
    end loadConfigFromDiskOrCreate

    val currentConfig = 
      val configs = loadConfigFromDiskOrCreate()
      val store = Concurrent.MutableKeyValueStore[String, String]()
      for (k, v) <- configs do store.put(k, v)
      store

    private def overwriteConfigWithCurrent(): Unit =
      Disk.saveLines(lines = currentConfig.toSeq.map((k,v) => s"$k=$v"), fileName = configFileName)

    def isDefinedAt(key: String): Boolean = currentConfig.isDefinedAt(key)

    def getString(key: String): Option[String] = currentConfig.get(key)

    def setString(key: String, value: String): Unit = synchronized:
      currentConfig.put(key, value)
      overwriteConfigWithCurrent()

    def getInt(key: String): Option[Int] = currentConfig.get(key).flatMap(_.toIntOption) 

    def setInt(key: String, value: Int): Unit = synchronized:
      currentConfig.put(key, value.toString)
      overwriteConfigWithCurrent()

  end Store

end Config
