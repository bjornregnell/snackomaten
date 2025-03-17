package snackomaten

object Terminal:
  def get(prompt: String = "> "): String = scala.io.StdIn.readLine(prompt)
  def put(s: String): Unit = println(s)
