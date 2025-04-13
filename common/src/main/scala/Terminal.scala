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

  def awaitInput(): String = util.Try(reader.readLine("", null: Character, "")).getOrElse(CtrlD)

  def awaitSecret(mask: Char = '*'): String = util.Try(reader.readLine("", mask)).getOrElse(CtrlD)

  def prompt(s: String, color: String = BLUE) = print(s"$color$s$RESET")

  def put(s: String): Unit = println(s)

  def newLine(): Unit = println()

  def putColor(s: String, color: String) = println(s"$color$s$RESET")

  def putGreen(s: String): Unit = putColor(s, GREEN)

  def putRed(s: String): Unit = putColor(s, RED)

  def putBlue(s: String): Unit = putColor(s, BLUE)

  def putYellow(s: String): Unit = putColor(s, YELLOW)

  def alert(s: String) = putColor(s, RED_B);

  def removeCompletions(): Unit = reader.setCompleter(null)

  def showError(e: Throwable, showStackTrace: Boolean = false): Unit =
    Terminal.putRed(s"ERROR: $e")
    if showStackTrace then 
      Terminal.putRed(Thread.currentThread().getStackTrace().array.mkString("\n"))


  def setCompletions(first: Seq[String], second: Seq[String]): Unit =
    removeCompletions()
    val sc1 = new jline.reader.impl.completer.StringsCompleter(first*)
    val sc2 = new jline.reader.impl.completer.StringsCompleter(second*)
    val ac =  new jline.reader.impl.completer.ArgumentCompleter(sc1, sc2)
    reader.setCompleter(ac)
