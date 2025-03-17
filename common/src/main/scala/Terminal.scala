package snackomaten


object Terminal:
  //https://github.com/jline/jline3/wiki
  //https://search.maven.org/artifact/org.jline/jline

  import org.jline.terminal.TerminalBuilder
  import org.jline.reader.LineReaderBuilder
  import org.jline.reader.impl.completer.{ArgumentCompleter, StringsCompleter}
  import org.jline.reader.impl.LineReaderImpl
  final val CtrlD = "\u0004"  // End Of Transmission

  val terminal = TerminalBuilder.terminal // builder.system(true).build
  val reader = LineReaderBuilder.builder
    .terminal(terminal)
    .build
    .asInstanceOf[LineReaderImpl] //cast hack to expose set/getCompleter

  def get(prompt: String = "snackomaten> ", default: String = ""): String =
    util.Try(reader.readLine(prompt, null: Character, default)).getOrElse(CtrlD)

  def getSecret(prompt: String = "Enter secret: ", mask: Char = '*'): String = 
    util.Try(reader.readLine(prompt, mask)).getOrElse(CtrlD)

  def isOk(msg: String = ""): Boolean = get(s"$msg (Y/n): ") == "Y"
  
  def put(s: String): Unit = terminal.writer().println(s)

  def removeCompletions(): Unit = reader.setCompleter(null)
  
  def setCompletions(first: Seq[String], second: Seq[String]): Boolean =
    removeCompletions()
    val sc1 = new StringsCompleter(first*)
    val sc2 = new StringsCompleter(second*)
    val ac = new ArgumentCompleter(sc1, sc2)
    reader.setCompleter(ac)
    true  // to be compatible with old readline which used to return if ok