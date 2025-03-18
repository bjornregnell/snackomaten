package snackomaten

/** Simple terminal features wrapping an underlying jline reader github.com/jline/jline3 */
object Terminal:
  import org.jline // https://github.com/jline/jline3/wiki

  val CtrlD = "\u0004"  // End Of Transmission
  export Console.*

  val terminal = jline.terminal.TerminalBuilder.terminal
  val reader = jline.reader.LineReaderBuilder.builder
    .terminal(terminal)
    .build
    .asInstanceOf[jline.reader.impl.LineReaderImpl] //cast hack to expose set/getCompleter

  def get(): String = util.Try(reader.readLine("", null: Character, "")).getOrElse(CtrlD)

  def getSecret(prompt: String = "Enter secret: ", mask: Char = '*'): String = 
    util.Try(reader.readLine(prompt, mask)).getOrElse(CtrlD)

  def prompt(s: String, color: String = BLUE) = print(s"$color$s$RESET")

  def put(s: String): Unit = println(s)

  def newLine(): Unit = println()

  def putColor(s: String, color: String) = println(s"$color$s$RESET")

  def putGreen(s: String): Unit = putColor(s, GREEN)

  def putRed(s: String): Unit = putColor(s, RED)

  def alert(s: String) = putColor(s, RED_B);

  def removeCompletions(): Unit = reader.setCompleter(null)

  def setCompletions(first: Seq[String], second: Seq[String]): Unit =
    removeCompletions()
    val sc1 = new jline.reader.impl.completer.StringsCompleter(first*)
    val sc2 = new jline.reader.impl.completer.StringsCompleter(second*)
    val ac =  new jline.reader.impl.completer.ArgumentCompleter(sc1, sc2)
    reader.setCompleter(ac)
