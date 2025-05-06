package snackomaten 

import java.io.IOException
import snackomaten.Disk.createDirIfNotExist
import snackomaten.SecureKeyValueStore.Vault.VaultOpt

object Server:
  val Dir = Disk.userDir() + "/snackomaten-server"
  createDirIfNotExist(Dir)
  val VaultFile   = s"$Dir/vault.txt"
  val MpwFile     = s"$Dir/mpv.txt"
  val UsersFile   = s"$Dir/users.tsv"
  val UpdatesFile = s"$Dir/updates.txt"
  val TestUser = User.fromSepValues(s"Svensson\tSven\tnomail@nowhere.com\t20010101-2345").get
  if !Disk.isExisting(UsersFile) then
    Terminal.putYellow(s"Creating $UsersFile")
    Disk.saveLines(Seq(User.toSepValues(TestUser)), fileName = UsersFile)

class Server(val port: Int, masterPassword: String):

  val vaultOpt: VaultOpt[String, String] = SecureKeyValueStore.Vault.openVaultOf[String, String](
      masterPw = masterPassword,
      mpwFile =   java.io.File(Server.MpwFile),
      vaultFile = java.io.File(Server.VaultFile),
      tmpFile =   java.io.File(Server.UpdatesFile), 
    )

  val vault: SecureKeyValueStore.Vault[String, String] = vaultOpt match
    case VaultOpt(Some(vault), isMasterPasswordFileCreated, saltOpt) => 
      if isMasterPasswordFileCreated then Terminal.putYellow(s"Created ${Server.MpwFile}")
      vault
    case _ =>
      Terminal.putRed(s"Failed to open ${Server.VaultFile}")
      System.exit(1)
      null

  val quit = Concurrent.MutableLatch()

  import scala.jdk.CollectionConverters.*
  val allConnections = Concurrent.MutableFifoSeq[Network.Connection]()

  def timestamp: String = java.util.Date().toString

  def outputPrompt: String = s"${Console.GREEN}[$timestamp]${Console.RESET}"

  lazy val serverPort = Network.openServerPort(port)

  def log(msg: String) = Terminal.put(s"$outputPrompt $msg")

  def startMsg(): Unit = log(s"Server started, port: $port")

  def spawnAcceptLoop() = Concurrent.Run:
    while quit.isFalse do 
      Network.Connection.awaitConnectClient(from = serverPort) match
        case Network.Failed(error) => Terminal.putRed(s"Error in spawnAcceptLoop: $error")
        case connection: Network.Connection => 
          log(s"New connection created: $connection")
          spawnReceiveLoop(connection)
          allConnections.put(connection)
          log(s"Number of connections: ${allConnections.size}")
    end while
    Terminal.putGreen("spawnAcceptLoop is terminating now.")

  def spawnReceiveLoop(connection: Network.Connection): Unit =  Concurrent.Run:
    def cleanUp(): Unit = 
      try
        allConnections.deleteIfPresent(connection)
        connection.close()
        Terminal.putYellow(s"Receive loop will terminate: $connection")
        log(s"Number of connections: ${allConnections.size}")
      catch case e: Throwable => Terminal.putRed(s"Error in spawnReceiveLoop cleanUp: $e")

    try
      while quit.isFalse && connection.isActive do
        val input = connection.awaitInput() 
        val t0 = System.currentTimeMillis()
        input match
          case Network.Failed(error) => 
            error match
              case _: java.io.EOFException => Terminal.putYellow("EOF in connection.read()")
              case _ => Terminal.showError(error)
            end match
            cleanUp()

          case msg: String =>
            log(s"Received raw message: '$msg'")
            //connection.write(s"[SERVER INFO] Snackomaten got your precious message.")
            for c <- allConnections do if c != connection then
              Terminal.putGreen(s"Broadcasting to other $c:") 
              Terminal.putYellow(msg)
              c.write(msg)
        end match
        val elapsed = System.currentTimeMillis() - t0
        val backoff = elapsed * allConnections.size
        log(s"Processing took $elapsed ms, virtual thread will sleep for $backoff ms to allow other threads to work")
        Thread.sleep(backoff)
      end while
      cleanUp()
    catch case e: Throwable => 
      Terminal.putRed(s"Unexpected error in spawnReceiveLoop: $e")
      cleanUp()
    end try
  end spawnReceiveLoop

  def spawnCommandLoop(): Unit = Concurrent.Run:
    Terminal.putYellow("Type Q <ENTER> to quit.")
    while 
      val input = Terminal.awaitInput()
      input != "Q"
    do ()
    quit.setTrue()
    
  def start(): Unit = 
    if !Disk.isExisting(Server.Dir) then 
      println(s"Creating Server.Dir: ${Server.Dir}")
      Disk.createDirIfNotExist(Server.Dir)
    else 
      println(s"Existing Server.Dir: ${Server.Dir}")

    startMsg() 
    spawnAcceptLoop()
    spawnCommandLoop()
    quit.waitUntilTrue() 
    log("Server terminates. Goodbye!")
