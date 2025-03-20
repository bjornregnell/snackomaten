package snackomaten 

object Settings:
  val default: Map[String, String] = Map(
    "globalHost" -> "bjornix.cs.lth.se",
    "globalPort" -> "8090",
  ) 

  def isValidKey(key: String): Boolean = 
    key.trim.nonEmpty && key.forall(c => c.isLetter || c.isDigit || c == '_' || c == '.' || c == '/')
  
  def isValidValue(value: String): Boolean = 
    value.trim.nonEmpty && value.forall(c => c != '=' && c != '\n')

  def badKeyValues(kvs: Map[String, String]): Seq[(String, String)] = kvs.filterNot((k, v) => isValidKey(k) && isValidValue(v)).toSeq

  assert(badKeyValues(default).isEmpty, s"illegal chars Settings.default: ${badKeyValues(default)}")

  object UserConfig:
    val configDir = Disk.userDir() + "/snackomaten"

    val configFileName = s"$configDir/config.txt"

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
      val store = Concurrent.ThreadSafe.MutableKeyValueStore[String, String]()
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

  end UserConfig

  def globalHost: Option[String] = UserConfig.getString("globalHost")
  
  def globalPort: Option[Int] = UserConfig.getInt("globalPort")

  def userId: Option[String] = UserConfig.getString("userId")

end Settings
