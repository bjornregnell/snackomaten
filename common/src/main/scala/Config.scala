package snackomaten 

object Config:
  def globalHost: Option[String] = Store.getString("globalHost")
  
  def globalPort: Option[Int] = Store.getInt("globalPort")

  def userId: Option[String] = Store.getString("userId")

  def javaMajorVersion(): Option[Int] = 
    Option(System.getProperty("java.version")).flatMap(_.takeWhile(_.isDigit).toIntOption)

  def checkJavaVersionOrAbort(minVersion: Int): Unit =
      for version <- javaMajorVersion() if version < minVersion do 
        Terminal.alert(s"Snackomaten requires at least Java 21")
        throw Error("Java upgrade needed.")

  object Store:
    val configDir = Disk.userDir() + "/snackomaten"

    val configFileName = s"$configDir/config.txt"

    val default: Map[String, String] = Map(
      "globalHost" -> "bjornix.cs.lth.se",
      "globalPort" -> "8090",
    ) 

    def isValidKey(key: String): Boolean = 
      key.trim.nonEmpty && key.forall(c => c.isLetter || c.isDigit || c == '_' || c == '.' || c == '/')
    
    def isValidValue(value: String): Boolean = 
      value.trim.nonEmpty && value.forall(c => c != '=' && c != '\n')

    def badKeyValues(kvs: Map[String, String]): Seq[(String, String)] = kvs.filterNot((k, v) => isValidKey(k) && isValidValue(v)).toSeq

    assert(badKeyValues(default).isEmpty, s"illegal chars Config.default: ${badKeyValues(default)}")

    def overwriteConfigWithDefault(): Unit =
      Disk.saveLines(lines = default.map((k,v) => s"$k=$v").toSeq, fileName = configFileName)

    def loadConfigFromDiskOrCreate(): Seq[(String, String)] = 
      if !Disk.isExisting(configDir) then Disk.createDirIfNotExist(configDir)
      
      def splitKeyValue(s: String): Option[(String, String)] = 
        val i = s.indexOf("=")
        if i < 1 || s.length < 2 then 
          Terminal.alert(s"Ignoring bad setting in $configFileName: $s")
          None
        else 
          val (k, v) = (s.substring(0, i), s.substring(i + 1))
          if isValidKey(k) && isValidValue(v) then Option(k -> v) else None

      if !Disk.isExisting(configFileName) then 
        overwriteConfigWithDefault()
        default.toSeq
      else 
        Disk.loadLines(fileName = configFileName).flatMap(splitKeyValue)
    end loadConfigFromDiskOrCreate

    private lazy val currentConfig = 
      val configs = loadConfigFromDiskOrCreate()
      val store = Concurrent.MutableKeyValueStore[String, String]()
      for (k, v) <- configs do store.put(k, v)
      store

    def overwriteConfigWithCurrent(): Unit =
      Disk.saveLines(lines = currentConfig.toSeq.map((k,v) => s"$k=$v"), fileName = configFileName)

    def isDefinedAt(key: String): Boolean = currentConfig.isDefinedAt(key) && default.isDefinedAt(key)

    def getString(key: String): Option[String] = currentConfig.get(key).orElse(default.get(key))

    def setString(key: String, value: String): Unit = 
      currentConfig.put(key, value)
      overwriteConfigWithCurrent()

    def getInt(key: String): Option[Int] = currentConfig.get(key).flatMap(_.toIntOption) match
      case None => 
        Terminal.alert(s"Bad setting $key in $configFileName using default.get($key)=${default.get(key)}"); 
        default.get(key).flatMap(_.toIntOption)
      case opt => opt

    def setInt(key: String, value: Int): Unit = 
      currentConfig.put(key, value.toString)
      overwriteConfigWithCurrent()

  end Store

end Config
