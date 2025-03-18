package snackomaten

import java.io.BufferedWriter
import java.io.FileWriter
import java.nio.charset.Charset

object Disk:
  // A copy-paste fork of the introprog-scalalib IO module excluding doc comments for readability
  // https://github.com/lunduniversity/introprog-scalalib/blob/master/src/main/scala/introprog/IO.scala

  def loadString(fileName: String, enc: String = "UTF-8"): String =
    var result: String = ""
    val source = scala.io.Source.fromFile(fileName, enc)
    try result = source.mkString finally source.close()
    result

  def loadLines(fileName: String, enc: String = "UTF-8"): Vector[String] =
    var result = Vector.empty[String]
    val source = scala.io.Source.fromFile(fileName, enc)
    try result = source.getLines().toVector finally source.close()
    result

  def saveString(text: String, fileName: String, enc: String = "UTF-8"): Unit =
    val f = new java.io.File(fileName)
    val pw = new java.io.PrintWriter(f, enc)
    try pw.write(text) finally pw.close()

  def saveLines(lines: Seq[String], fileName: String, enc: String = "UTF-8"): Unit =
    if lines.nonEmpty then saveString(lines.mkString("", "\n", "\n"), fileName, enc)

  def appendString(text: String, fileName: String, enc: String = "UTF-8"): Unit =
    val f = new java.io.File(fileName);
    require(!f.isDirectory(), "The file you're trying to write to can't be a directory.")
    val w =
      if f.exists() then
        new BufferedWriter(new FileWriter(fileName, Charset.forName(enc), true))
      else
        new java.io.PrintWriter(f, enc)
    try w.write(text) finally w.close()

  def appendLines(lines: Seq[String], fileName: String, enc: String = "UTF-8"): Unit =
    if lines.nonEmpty then appendString(lines.mkString("","\n","\n"), fileName, enc)

  def loadObject[T](fileName: String): T =
    val f = new java.io.File(fileName)
    val ois = new java.io.ObjectInputStream(new java.io.FileInputStream(f))
    try ois.readObject.asInstanceOf[T] finally ois.close()

  def saveObject[T](obj: T, fileName: String): Unit =
    val f = new java.io.File(fileName)
    val oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(f))
    try oos.writeObject(obj) finally oos.close()

  def isExisting(fileName: String): Boolean = new java.io.File(fileName).exists

  def createDirIfNotExist(dir: String): Boolean = new java.io.File(dir).mkdirs()

  def userDir(): String = System.getProperty("user.home")

  def currentDir(): String =
    java.nio.file.Paths.get(".").toAbsolutePath.normalize.toString

  def list(dir: String = "."): Vector[String] =
    Option(new java.io.File(dir).list).map(_.toVector).getOrElse(Vector())

  def move(from: String, to: String): Unit =
    import java.nio.file.{Files, Paths, StandardCopyOption}
    Files.move(Paths.get(from), Paths.get(to), StandardCopyOption.REPLACE_EXISTING)

  def delete(fileName: String): Unit =
    import java.nio.file.{Files, Paths}
    Files.delete(Paths.get(fileName))

