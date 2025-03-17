package snackomaten 

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import java.net.Socket

class Client(val host: String = "bjornix.cs.lth.se", val port: Int = 8090) {

  val cmdDelayMillis: Long = 50L
  val cmdPrompt: String ="Spam> "

  @volatile private var connectionOpt: Option[Network.Connection] = None

  def connect() = {
    val sock = new Socket(host, port)
    connectionOpt = Some(Network.Connection.fromSocket(sock))
    Terminal.put(s"Connected to $host:$port")
  }

  def receive(): String = connectionOpt.get.read

  def expect(prefix: String): Either[String, String] = {
    val msg = receive()
    if (msg.startsWith(prefix)) Right(msg.stripPrefix(prefix))
    else Left(msg)
  }

  def send(msg: String): Unit = {
    Terminal.put(s"$connectionOpt: isActive=${connectionOpt.get.isActive}")
    connectionOpt.get.write(msg)
  }

  def connectionFailed() = Try {
    Terminal.put("Connection failed!")
    connectionOpt.get.close()
    connectionOpt = None
  }

  def spawnReceiveLoop() = Future {
    while (true) {
      val msg = receive()
      Terminal.put(s"\n$msg")
    }
  }

  def cmdLoop(): Unit = 
    var continue = true
    while continue do
      val cmd = Terminal.get(cmdPrompt)
      if cmd == null then continue = false else
        send(cmd)
        Thread.sleep(cmdDelayMillis);
    end while


  def start(): Unit = {
    Terminal.put("Attempting to connect to server...")
    connect()
    Terminal.put("Connected to snackomaten server!")
    spawnReceiveLoop()
    cmdLoop()
  }
}