package snackomaten 

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import java.net.Socket

class Client(val host: String = "bjornix.cs.lth.se", val port: Int = 8090) {

  val isListening = Concurrent.ThreadSafe.MutableFlag(false)

  @volatile private var connectionOpt: Option[Network.Connection] = None

  def connect() = {
    val sock = new Socket(host, port)
    connectionOpt = Some(Network.Connection.fromSocket(sock))
    Terminal.putGreen(s"Connected to $host:$port")
  }

  def send(msg: String): Unit = {
    Terminal.putGreen(s"Sending $msg via $connectionOpt: isActive=${connectionOpt.get.isActive}")
    connectionOpt.get.write(msg)
  }

  def spawnReceiveLoop() = 
    Future:
      Terminal.putRed("New thread started!")
      while isListening.isTrue do
        Terminal.putRed(s"Listening for messages until Ctrl+D in ${Thread.currentThread()}")
        val msg = connectionOpt.get.read()
        Terminal.put(s"Received: '$msg'")
      end while
      Terminal.putGreen("Listening mode stopped.")

  def helpText = """
    Type !listen to start concurrent receive loop 
    Type anything else to spam all connected clients with a message.
  """

  def commandLoop(): Unit = 
    var continue = true
    while continue do
      if isListening.isTrue 
      then Terminal.prompt("snack> ", color = Terminal.RED) 
      else Terminal.prompt("snack> ", color = Terminal.BLUE)
      val cmd = Terminal.get()
      if cmd == Terminal.CtrlD then 
        continue = false 
      else if cmd == "" then 
        println(helpText)
      else if cmd == "!listen" then 
        println("DEBUG: entering eternal receiveLoop")
        if isListening.isFalse then 
          isListening.setTrue()
          spawnReceiveLoop()
        else 
          Terminal.alert("Already in listening mode!")
          Terminal.putRed("Type !stop to stop listening.")
      else if cmd == "!stop" then
        Terminal.putGreen("Stopping listening mode...")
        isListening.setFalse()
      else send(cmd)
    end while
    connectionOpt.get.close()

  def start(): Unit = {
    Terminal.put(s"Attempting to connect to server host=$host port=$port")
    connect()
    Terminal.putGreen("Successfully connected to snackomaten server!")
    Terminal.put(helpText)
    commandLoop()
  }
}